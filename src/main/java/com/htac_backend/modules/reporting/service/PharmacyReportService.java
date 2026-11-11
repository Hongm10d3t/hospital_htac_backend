package com.htac_backend.modules.reporting.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.reporting.dto.PurchaseRequestReportRow;
import com.htac_backend.modules.reporting.dto.ReportPage;
import com.htac_backend.modules.reporting.dto.StockBalanceReportRow;
import com.htac_backend.modules.reporting.dto.StockMovementReportLine;
import com.htac_backend.modules.reporting.dto.StockMovementReportRow;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PharmacyReportService {
    // The same signed POSTED movements used by M06 inventory: transfers have two positions.
    private static final String MOVEMENT_FROM = """
            FROM (
                SELECT d.id AS document_id, d.document_code, i.id AS item_id,
                    d.type, d.occurred_at, b.medicine_id, i.batch_id,
                    d.stock_location, d.stock_bucket,
                    CASE WHEN d.type IN ('OPENING','RECEIPT','PATIENT_RETURN','ADJUSTMENT')
                        THEN i.quantity ELSE -i.quantity END AS delta_quantity
                FROM stock_document_item i JOIN stock_document d ON d.id = i.document_id
                    JOIN medicine_batch b ON b.id = i.batch_id
                WHERE d.status = 'POSTED'
                UNION ALL
                SELECT d.id, d.document_code, i.id, d.type, d.occurred_at,
                    b.medicine_id, i.batch_id, d.destination_location, d.destination_bucket,
                    i.quantity
                FROM stock_document_item i JOIN stock_document d ON d.id = i.document_id
                    JOIN medicine_batch b ON b.id = i.batch_id
                WHERE d.status = 'POSTED' AND d.type = 'TRANSFER'
            ) movement
            """;
    private static final Set<String> TYPES = Set.of("OPENING", "RECEIPT", "TRANSFER", "DISPENSE",
            "PATIENT_RETURN", "SUPPLIER_RETURN", "DISPOSAL", "ADJUSTMENT");
    private static final Set<String> REQUEST_STATUSES = Set.of("DRAFT", "PENDING", "APPROVED",
            "REJECTED", "CANCELLED");
    private final ReportQuerySupport query;
    private final AccountRoleLookupService accounts;

    public PharmacyReportService(ReportQuerySupport query, AccountRoleLookupService accounts) {
        this.query = query;
        this.accounts = accounts;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
    @Transactional(readOnly = true)
    public PageResponse<StockBalanceReportRow> balances(Long medicineId, Long batchId,
            String location, String bucket, PageQuery page) {
        requireDrugReader();
        validatePosition(medicineId, batchId, location, bucket);
        var args = positionArgs(medicineId, batchId, location, bucket)
                .addValue("today", LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        String sql = """
                SELECT movement.medicine_id, m.code AS medicine_code, movement.batch_id,
                    b.batch_number, b.expiry_date, b.status AS batch_status,
                    movement.stock_location, movement.stock_bucket,
                    SUM(movement.delta_quantity) AS physical_quantity,
                    SUM(CASE WHEN m.is_active = TRUE AND b.status = 'ACTIVE'
                        AND b.expiry_date > :today AND movement.stock_bucket = 'AVAILABLE'
                        THEN movement.delta_quantity ELSE 0 END) AS dispensable_quantity
                """ + MOVEMENT_FROM + """
                JOIN medicine m ON m.id = movement.medicine_id
                JOIN medicine_batch b ON b.id = movement.batch_id
                WHERE (:medicineId IS NULL OR movement.medicine_id = :medicineId)
                    AND (:batchId IS NULL OR movement.batch_id = :batchId)
                    AND (:location IS NULL OR movement.stock_location = :location)
                    AND (:bucket IS NULL OR movement.stock_bucket = :bucket)
                GROUP BY movement.medicine_id, m.code, movement.batch_id, b.batch_number,
                    b.expiry_date, b.status, movement.stock_location, movement.stock_bucket,
                    m.is_active
                HAVING SUM(movement.delta_quantity) <> 0
                """;
        return query.page(sql, args, balanceSort(page),
                "medicine_id, batch_id, stock_location, stock_bucket", page,
                (rs, number) -> new StockBalanceReportRow(rs.getLong("medicine_id"),
                        rs.getString("medicine_code"), rs.getLong("batch_id"),
                        rs.getString("batch_number"), rs.getDate("expiry_date").toLocalDate(),
                        rs.getString("batch_status"), rs.getString("stock_location"),
                        rs.getString("stock_bucket"), rs.getBigDecimal("physical_quantity"),
                        rs.getBigDecimal("dispensable_quantity")));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
    @Transactional(readOnly = true)
    public ReportPage<StockMovementReportRow> movements(LocalDate from, LocalDate to,
            Long medicineId, Long batchId, String location, String bucket,
            String type, PageQuery page) {
        requireDrugReader();
        ReportWindow window = ReportWindow.of(from, to);
        validatePosition(medicineId, batchId, location, bucket);
        validateType(type);
        var args = movementArgs(window, medicineId, batchId, location, bucket, type);
        String sql = """
                SELECT movement.medicine_id, movement.batch_id, movement.stock_location,
                    movement.stock_bucket, movement.type,
                    SUM(CASE WHEN movement.delta_quantity > 0 THEN movement.delta_quantity
                        ELSE 0 END) AS in_quantity,
                    SUM(CASE WHEN movement.delta_quantity < 0 THEN -movement.delta_quantity
                        ELSE 0 END) AS out_quantity,
                    SUM(movement.delta_quantity) AS net_quantity,
                    COUNT(DISTINCT movement.document_id) AS document_count
                """ + MOVEMENT_FROM + movementFilter() + """
                GROUP BY movement.medicine_id, movement.batch_id, movement.stock_location,
                    movement.stock_bucket, movement.type
                """;
        var result = query.page(sql, args, movementSort(page),
                "medicine_id, batch_id, stock_location, stock_bucket, type", page,
                (rs, number) -> new StockMovementReportRow(rs.getLong("medicine_id"),
                        rs.getLong("batch_id"), rs.getString("stock_location"),
                        rs.getString("stock_bucket"), rs.getString("type"),
                        rs.getBigDecimal("in_quantity"), rs.getBigDecimal("out_quantity"),
                        rs.getBigDecimal("net_quantity"), rs.getLong("document_count")));
        return ReportPage.of(from, to, "stock_document.occurred_at; POSTED only", result);
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
    @Transactional(readOnly = true)
    public ReportPage<StockMovementReportLine> movementLines(LocalDate from, LocalDate to,
            Long medicineId, Long batchId, String location, String bucket,
            String type, PageQuery page) {
        requireDrugReader();
        ReportWindow window = ReportWindow.of(from, to);
        validatePosition(medicineId, batchId, location, bucket);
        validateType(type);
        var args = movementArgs(window, medicineId, batchId, location, bucket, type);
        String sql = """
                SELECT movement.document_id, movement.document_code, movement.item_id,
                    movement.type, movement.occurred_at, movement.medicine_id,
                    movement.batch_id, movement.stock_location, movement.stock_bucket,
                    movement.delta_quantity
                """ + MOVEMENT_FROM + movementFilter();
        var result = query.page(sql, args, lineSort(page),
                "document_id, item_id, stock_location, stock_bucket", page,
                (rs, number) -> new StockMovementReportLine(rs.getLong("document_id"),
                        rs.getString("document_code"), rs.getLong("item_id"),
                        rs.getString("type"), rs.getTimestamp("occurred_at").toInstant(),
                        rs.getLong("medicine_id"), rs.getLong("batch_id"),
                        rs.getString("stock_location"), rs.getString("stock_bucket"),
                        rs.getBigDecimal("delta_quantity")));
        return ReportPage.of(from, to, "stock_document.occurred_at; POSTED only", result);
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
    @Transactional(readOnly = true)
    public PageResponse<PurchaseRequestReportRow> purchaseRequests(String status, Long supplierId,
            LocalDate expectedFrom, LocalDate expectedTo, PageQuery page) {
        requireDrugReader();
        if (status != null && !REQUEST_STATUSES.contains(status)) {
            throw new InvalidRequestException("Trạng thái đề xuất không hợp lệ");
        }
        validId(supplierId);
        if (expectedFrom != null && expectedTo != null && !expectedTo.isAfter(expectedFrom)) {
            throw new InvalidRequestException("Khoảng ngày dự kiến không hợp lệ");
        }
        var args = new MapSqlParameterSource("status", status).addValue("supplierId", supplierId)
                .addValue("expectedFrom", expectedFrom).addValue("expectedTo", expectedTo);
        String sql = """
                SELECT r.id AS request_id, r.request_code, r.status, r.supplier_id,
                    ri.medicine_id, r.reviewed_at, r.ordered_at, r.expected_delivery_date,
                    r.closed_at, ri.quantity AS requested_quantity,
                    CASE WHEN r.status = 'APPROVED' THEN ri.quantity ELSE 0 END AS approved_quantity,
                    COALESCE(rec.received_quantity, 0) AS received_quantity,
                    CASE WHEN r.status = 'APPROVED' THEN ri.quantity - COALESCE(rec.received_quantity, 0)
                        ELSE 0 END AS remaining_quantity
                FROM purchase_request r JOIN purchase_request_item ri ON ri.request_id = r.id
                LEFT JOIN (
                    SELECT d.purchase_request_id, b.medicine_id,
                        SUM(i.quantity) AS received_quantity
                    FROM stock_document d JOIN stock_document_item i ON i.document_id = d.id
                        JOIN medicine_batch b ON b.id = i.batch_id
                    WHERE d.type = 'RECEIPT' AND d.status = 'POSTED'
                        AND d.purchase_request_id IS NOT NULL
                    GROUP BY d.purchase_request_id, b.medicine_id
                ) rec ON rec.purchase_request_id = r.id AND rec.medicine_id = ri.medicine_id
                WHERE (:status IS NULL OR r.status = :status)
                    AND (:supplierId IS NULL OR r.supplier_id = :supplierId)
                    AND (:expectedFrom IS NULL OR r.expected_delivery_date >= :expectedFrom)
                    AND (:expectedTo IS NULL OR r.expected_delivery_date < :expectedTo)
                """;
        return query.page(sql, args, requestSort(page), "request_id, medicine_id", page,
                (rs, number) -> new PurchaseRequestReportRow(rs.getLong("request_id"),
                        rs.getString("request_code"), rs.getString("status"),
                        (Long) rs.getObject("supplier_id"), rs.getLong("medicine_id"),
                        timestamp(rs, "reviewed_at"), timestamp(rs, "ordered_at"),
                        rs.getDate("expected_delivery_date") == null ? null
                                : rs.getDate("expected_delivery_date").toLocalDate(),
                        timestamp(rs, "closed_at"), rs.getBigDecimal("requested_quantity"),
                        rs.getBigDecimal("approved_quantity"), rs.getBigDecimal("received_quantity"),
                        rs.getBigDecimal("remaining_quantity")));
    }

    private java.time.Instant timestamp(java.sql.ResultSet rs, String field)
            throws java.sql.SQLException {
        var value = rs.getTimestamp(field);
        return value == null ? null : value.toInstant();
    }

    private String movementFilter() {
        return """
                WHERE movement.occurred_at >= :start AND movement.occurred_at < :end
                    AND (:medicineId IS NULL OR movement.medicine_id = :medicineId)
                    AND (:batchId IS NULL OR movement.batch_id = :batchId)
                    AND (:location IS NULL OR movement.stock_location = :location)
                    AND (:bucket IS NULL OR movement.stock_bucket = :bucket)
                    AND (:type IS NULL OR movement.type = :type)
                """;
    }

    private MapSqlParameterSource positionArgs(Long medicineId, Long batchId,
            String location, String bucket) {
        return new MapSqlParameterSource("medicineId", medicineId).addValue("batchId", batchId)
                .addValue("location", location).addValue("bucket", bucket);
    }

    private MapSqlParameterSource movementArgs(ReportWindow window, Long medicineId,
            Long batchId, String location, String bucket, String type) {
        return positionArgs(medicineId, batchId, location, bucket)
                .addValue("start", window.startSql()).addValue("end", window.endSql())
                .addValue("type", type);
    }

    private void requireDrugReader() {
        Long id = query.actorId();
        if (!accounts.hasActiveRole(id, AccountRole.ADMIN)
                && !accounts.hasActiveRole(id, AccountRole.PHARMACIST)) {
            throw new AccessDeniedException("Không có quyền xem báo cáo dược");
        }
    }

    private void validatePosition(Long medicineId, Long batchId, String location, String bucket) {
        validId(medicineId);
        validId(batchId);
        if (location != null && !Set.of("MAIN", "COUNTER").contains(location)) {
            throw new InvalidRequestException("Điểm kho không hợp lệ");
        }
        if (bucket != null && !Set.of("AVAILABLE", "QUARANTINE").contains(bucket)) {
            throw new InvalidRequestException("Nhóm tồn không hợp lệ");
        }
    }

    private void validateType(String type) {
        if (type != null && !TYPES.contains(type)) {
            throw new InvalidRequestException("Loại phiếu không hợp lệ");
        }
    }

    private void validId(Long id) {
        if (id != null && id <= 0) throw new InvalidRequestException("ID lọc không hợp lệ");
    }

    private String balanceSort(PageQuery page) {
        return switch (page.sortBy()) {
            case "medicineId" -> "medicine_id";
            case "batchId" -> "batch_id";
            case "physicalQuantity" -> "physical_quantity";
            default -> throw new InvalidRequestException("Trường sắp xếp không hợp lệ");
        };
    }

    private String movementSort(PageQuery page) {
        return switch (page.sortBy()) {
            case "medicineId" -> "medicine_id";
            case "batchId" -> "batch_id";
            case "type" -> "type";
            case "netQuantity" -> "net_quantity";
            default -> throw new InvalidRequestException("Trường sắp xếp không hợp lệ");
        };
    }

    private String lineSort(PageQuery page) {
        return switch (page.sortBy()) {
            case "occurredAt" -> "occurred_at";
            case "documentId" -> "document_id";
            case "itemId" -> "item_id";
            default -> throw new InvalidRequestException("Trường sắp xếp không hợp lệ");
        };
    }

    private String requestSort(PageQuery page) {
        return switch (page.sortBy()) {
            case "requestId" -> "request_id";
            case "expectedDeliveryDate" -> "expected_delivery_date";
            case "status" -> "status";
            default -> throw new InvalidRequestException("Trường sắp xếp không hợp lệ");
        };
    }
}
