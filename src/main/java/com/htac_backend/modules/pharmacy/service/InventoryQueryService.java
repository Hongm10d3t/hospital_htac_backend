package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.pharmacy.dto.ExpiryAlertResponse;
import com.htac_backend.modules.pharmacy.dto.InventoryPositionResponse;
import com.htac_backend.modules.pharmacy.dto.MedicineInventoryResponse;
import com.htac_backend.modules.pharmacy.dto.StockMovementResponse;
import com.htac_backend.modules.pharmacy.entity.Medicine;
import com.htac_backend.modules.pharmacy.entity.MedicineBatch;
import com.htac_backend.modules.pharmacy.repository.MedicineBatchRepository;
import com.htac_backend.modules.pharmacy.repository.MedicineRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryQueryService {
    private static final String MOVEMENT_CTE = """
            WITH movement AS (
                SELECT d.id AS document_id, d.document_code, i.id AS item_id, d.type,
                    d.occurred_at, d.stock_location, d.stock_bucket,
                    CASE WHEN d.type IN ('OPENING','RECEIPT','PATIENT_RETURN','ADJUSTMENT')
                        THEN i.quantity ELSE -i.quantity END AS delta_quantity
                FROM stock_document_item i JOIN stock_document d ON d.id = i.document_id
                WHERE i.batch_id = ? AND d.status = 'POSTED'
                UNION ALL
                SELECT d.id, d.document_code, i.id, d.type, d.occurred_at,
                    d.destination_location, d.destination_bucket, i.quantity
                FROM stock_document_item i JOIN stock_document d ON d.id = i.document_id
                WHERE i.batch_id = ? AND d.status = 'POSTED' AND d.type = 'TRANSFER'
            )
            """;

    private final MedicineRepository medicines;
    private final MedicineBatchRepository batches;
    private final MedicineBatchService batchRules;
    private final JdbcTemplate jdbc;
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;
    private final int expiryWarningDays;

    public InventoryQueryService(MedicineRepository medicines, MedicineBatchRepository batches,
            MedicineBatchService batchRules, JdbcTemplate jdbc, CurrentPharmacistActor actor,
            AccountRoleLookupService accounts,
            @Value("${his.pharmacy.expiry-warning-days:30}") int expiryWarningDays) {
        if (expiryWarningDays < 0 || expiryWarningDays > 3650) {
            throw new IllegalArgumentException("expiry-warning-days phải từ 0 đến 3650");
        }
        this.medicines = medicines;
        this.batches = batches;
        this.batchRules = batchRules;
        this.jdbc = jdbc;
        this.actor = actor;
        this.accounts = accounts;
        this.expiryWarningDays = expiryWarningDays;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(readOnly = true)
    public PageResponse<MedicineInventoryResponse> search(String term, Boolean active, PageQuery query) {
        requirePharmacist();
        var page = medicines.search(term == null || term.isBlank() ? null : term.trim(),
                active, query.toPageRequest());
        Map<Long, MedicineInventoryResponse> summaries = summarize(page.getContent());
        return PageResponse.from(page.map(medicine -> summaries.get(medicine.getId())));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(readOnly = true)
    public MedicineInventoryResponse medicine(Long medicineId) {
        requirePharmacist();
        Medicine medicine = medicines.findById(medicineId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thuốc"));
        return summarize(List.of(medicine)).get(medicineId);
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> movements(Long batchId, String location,
            String bucket, PageQuery query) {
        requirePharmacist();
        if (!batches.existsById(batchId)) {
            throw new NotFoundException("Không tìm thấy lô thuốc");
        }
        if ((location != null && !Set.of("MAIN", "COUNTER").contains(location))
                || (bucket != null && !Set.of("AVAILABLE", "QUARANTINE").contains(bucket))) {
            throw new InvalidRequestException("Vị trí hoặc nhóm tồn không hợp lệ");
        }
        String filter = " WHERE (? IS NULL OR stock_location = ?) "
                + "AND (? IS NULL OR stock_bucket = ?)";
        Object[] arguments = {batchId, batchId, location, location, bucket, bucket};
        Long total = jdbc.queryForObject(MOVEMENT_CTE + "SELECT COUNT(*) FROM movement" + filter,
                Long.class, arguments);
        int totalPages = (int) ((total + query.size() - 1) / query.size());
        if (query.page() > 0 && query.page() >= totalPages) {
            throw new InvalidRequestException("Trang yêu cầu vượt phạm vi");
        }
        String order = switch (query.sortBy()) {
            case "documentId" -> "document_id";
            case "itemId" -> "item_id";
            default -> "occurred_at";
        };
        String sql = MOVEMENT_CTE + "SELECT * FROM movement" + filter + " ORDER BY " + order
                + (query.direction().isAscending() ? " ASC" : " DESC")
                + ", document_id DESC, item_id DESC LIMIT ? OFFSET ?";
        Object[] pageArgs = {batchId, batchId, location, location, bucket, bucket,
                query.size(), query.page() * query.size()};
        var rows = jdbc.query(sql, (rs, rowNum) -> new StockMovementResponse(
                rs.getLong("document_id"), rs.getString("document_code"),
                rs.getLong("item_id"), rs.getString("type"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("stock_location"), rs.getString("stock_bucket"),
                rs.getBigDecimal("delta_quantity")), pageArgs);
        return new PageResponse<>(rows, query.page(), query.size(), total, totalPages);
    }

    private Map<Long, MedicineInventoryResponse> summarize(List<Medicine> selected) {
        if (selected.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = selected.stream().map(Medicine::getId).toList();
        List<MedicineBatch> allBatches = batches.findByMedicineIdIn(ids);
        Map<Long, List<PositionBalance>> byBatch = postedBalances(ids);
        LocalDate today = batchRules.businessDate();
        Map<Long, MedicineInventoryResponse> result = new HashMap<>();
        for (Medicine medicine : selected) {
            List<InventoryPositionResponse> positions = new ArrayList<>();
            List<ExpiryAlertResponse> alerts = new ArrayList<>();
            BigDecimal physical = BigDecimal.ZERO;
            BigDecimal dispensable = BigDecimal.ZERO;
            for (MedicineBatch batch : allBatches) {
                if (!batch.getMedicine().getId().equals(medicine.getId())) {
                    continue;
                }
                BigDecimal batchPhysical = BigDecimal.ZERO;
                for (PositionBalance balance : byBatch.getOrDefault(batch.getId(), List.of())) {
                    if (balance.quantity().signum() == 0) {
                        continue;
                    }
                    boolean eligible = "AVAILABLE".equals(balance.bucket())
                            && batchRules.isDispensable(batch, today);
                    BigDecimal usable = eligible ? balance.quantity() : BigDecimal.ZERO;
                    positions.add(new InventoryPositionResponse(batch.getId(), batch.getBatchNumber(),
                            batch.getExpiryDate(), batch.getStatus(), balance.location(),
                            balance.bucket(), balance.quantity(), usable));
                    physical = physical.add(balance.quantity());
                    dispensable = dispensable.add(usable);
                    batchPhysical = batchPhysical.add(balance.quantity());
                }
                if (batchPhysical.signum() > 0
                        && !batch.getExpiryDate().isAfter(today.plusDays(expiryWarningDays))) {
                    long days = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
                    alerts.add(new ExpiryAlertResponse(batch.getId(), batch.getBatchNumber(),
                            batch.getExpiryDate(), days, days <= 0, batchPhysical));
                }
            }
            positions.sort(Comparator.comparing(InventoryPositionResponse::batchId)
                    .thenComparing(InventoryPositionResponse::stockLocation)
                    .thenComparing(InventoryPositionResponse::stockBucket));
            alerts.sort(Comparator.comparing(ExpiryAlertResponse::expiryDate)
                    .thenComparing(ExpiryAlertResponse::batchId));
            result.put(medicine.getId(), new MedicineInventoryResponse(medicine.getId(),
                    medicine.getCode(), medicine.getName(), medicine.getBaseUnit(),
                    medicine.isActive(), medicine.getMinimumStock(), physical, dispensable,
                    medicine.isActive() && dispensable.compareTo(medicine.getMinimumStock()) < 0,
                    expiryWarningDays, alerts, positions));
        }
        return result;
    }

    private Map<Long, List<PositionBalance>> postedBalances(Collection<Long> medicineIds) {
        String placeholders = String.join(",", Collections.nCopies(medicineIds.size(), "?"));
        String sql = """
                SELECT movement.batch_id, movement.stock_location, movement.stock_bucket,
                    SUM(movement.delta_quantity) AS quantity
                FROM (
                    SELECT i.batch_id, d.stock_location, d.stock_bucket,
                        CASE WHEN d.type IN ('OPENING','RECEIPT','PATIENT_RETURN','ADJUSTMENT')
                            THEN i.quantity ELSE -i.quantity END AS delta_quantity
                    FROM stock_document_item i JOIN stock_document d ON d.id = i.document_id
                    JOIN medicine_batch b ON b.id = i.batch_id
                    WHERE d.status = 'POSTED' AND b.medicine_id IN (%s)
                    UNION ALL
                    SELECT i.batch_id, d.destination_location, d.destination_bucket, i.quantity
                    FROM stock_document_item i JOIN stock_document d ON d.id = i.document_id
                    JOIN medicine_batch b ON b.id = i.batch_id
                    WHERE d.status = 'POSTED' AND d.type = 'TRANSFER' AND b.medicine_id IN (%s)
                ) movement
                GROUP BY movement.batch_id, movement.stock_location, movement.stock_bucket
                """.formatted(placeholders, placeholders);
        List<Object> args = new ArrayList<>(medicineIds);
        args.addAll(medicineIds);
        Map<Long, List<PositionBalance>> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            Long batchId = rs.getLong("batch_id");
            result.computeIfAbsent(batchId, ignored -> new ArrayList<>()).add(
                    new PositionBalance(rs.getString("stock_location"),
                            rs.getString("stock_bucket"), rs.getBigDecimal("quantity")));
        }, args.toArray());
        return result;
    }

    private void requirePharmacist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.PHARMACIST)) {
            throw new BusinessException("PHARMACIST_INACTIVE", "Tài khoản dược sĩ không hoạt động");
        }
    }

    private record PositionBalance(String location, String bucket, BigDecimal quantity) { }
}
