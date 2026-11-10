package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.dto.PrescriptionLineResponse;
import com.htac_backend.modules.outpatient.service.IssuedPrescriptionService;
import com.htac_backend.modules.pharmacy.dto.DispenseStockRequest;
import com.htac_backend.modules.pharmacy.dto.DispensingLineResponse;
import com.htac_backend.modules.pharmacy.dto.DispensingSnapshotResponse;
import com.htac_backend.modules.pharmacy.dto.StockDocumentResponse;
import com.htac_backend.modules.pharmacy.entity.MedicineBatch;
import com.htac_backend.modules.pharmacy.repository.StockDocumentItemRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispenseStockService {
    private final IssuedPrescriptionService prescriptions;
    private final MedicineBatchService batches;
    private final DispensedQuantityService dispensed;
    private final StockBalanceService balances;
    private final StockDraftFactory factory;
    private final StockDocumentItemRepository items;
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;

    public DispenseStockService(IssuedPrescriptionService prescriptions, MedicineBatchService batches,
            DispensedQuantityService dispensed, StockBalanceService balances,
            StockDraftFactory factory, StockDocumentItemRepository items,
            CurrentPharmacistActor actor, AccountRoleLookupService accounts) {
        this.prescriptions = prescriptions;
        this.batches = batches;
        this.dispensed = dispensed;
        this.balances = balances;
        this.factory = factory;
        this.items = items;
        this.actor = actor;
        this.accounts = accounts;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DispensingSnapshotResponse snapshot(Long prescriptionId) {
        requirePharmacist();
        var prescription = prescriptions.lockForDispensing(prescriptionId);
        return new DispensingSnapshotResponse(prescriptionId,
                prescription.dispensingCheckedAt(), prescription.dispensingCheckedAt() != null,
                prescription.items().stream()
                .map(line -> {
                    BigDecimal used = dispensed.postedQuantity(line.id());
                    return new DispensingLineResponse(line.id(), line.medicineId(), line.medicineName(),
                            line.baseUnit(), line.quantity(), used, line.quantity().subtract(used),
                            line.unfilledQuantity(), line.unfilledReason(), line.unfilledNote());
                }).toList());
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockDocumentResponse create(DispenseStockRequest body) {
        requirePharmacist();
        if (body == null || body.prescriptionId() == null || body.occurredAt() == null
                || body.items() == null || body.items().isEmpty()) {
            throw new InvalidRequestException("Phiếu cấp thuốc không hợp lệ");
        }
        var prescription = prescriptions.lockForDispensing(body.prescriptionId());
        if (body.occurredAt().toInstant().isBefore(prescription.issuedAt())) {
            throw new InvalidRequestException("Thời điểm cấp trước khi phát hành đơn");
        }
        if (body.items().stream().anyMatch(line -> line == null || line.batchId() == null)) {
            throw new InvalidRequestException("Dòng cấp thuốc không hợp lệ");
        }
        Map<Long, PrescriptionLineResponse> lines = prescription.items().stream()
                .collect(Collectors.toMap(PrescriptionLineResponse::id, Function.identity()));
        Map<Long, MedicineBatch> lockedBatches = new HashMap<>();
        var today = batches.businessDate();
        body.items().stream().map(line -> line.batchId()).distinct().sorted()
                .forEach(id -> lockedBatches.put(id, batches.requireDispensable(id, today)));
        Map<Long, BigDecimal> byLine = new HashMap<>();
        Map<Long, BigDecimal> byBatch = new HashMap<>();
        List<StockDraftFactory.DraftLine> draftLines = body.items().stream().map(line -> {
            if (line == null || line.prescriptionItemId() == null || line.batchId() == null
                    || line.quantity() == null || line.quantity().signum() <= 0) {
                throw new InvalidRequestException("Dòng cấp thuốc không hợp lệ");
            }
            PrescriptionLineResponse prescribed = lines.get(line.prescriptionItemId());
            MedicineBatch batch = lockedBatches.get(line.batchId());
            if (prescribed == null || batch == null
                    || !prescribed.medicineId().equals(batch.getMedicine().getId())) {
                throw new BusinessException("DISPENSE_LINE_MISMATCH",
                        "Dòng đơn hoặc lô không khớp thuốc trong đơn");
            }
            byLine.merge(line.prescriptionItemId(), line.quantity(), BigDecimal::add);
            byBatch.merge(line.batchId(), line.quantity(), BigDecimal::add);
            return new StockDraftFactory.DraftLine(line.batchId(), line.quantity(), null,
                    line.prescriptionItemId(), null, null, null, null);
        }).toList();
        for (var entry : byLine.entrySet()) {
            BigDecimal allowed = lines.get(entry.getKey()).quantity()
                    .subtract(dispensed.postedQuantity(entry.getKey()));
            if (entry.getValue().compareTo(allowed) > 0) {
                throw new BusinessException("DISPENSE_EXCEEDS_PRESCRIPTION",
                        "Lượng cấp vượt lượng còn lại của dòng đơn");
            }
        }
        for (var entry : byBatch.entrySet()) {
            BigDecimal onHand = balances.balance(new StockPosition(entry.getKey(),
                    "COUNTER", "AVAILABLE"));
            if (entry.getValue().compareTo(onHand) > 0) {
                throw new BusinessException("INSUFFICIENT_STOCK", "Tồn tại quầy không đủ để cấp");
            }
        }
        var document = factory.create("DISPENSE", body.occurredAt().toInstant(),
                "COUNTER", "AVAILABLE", null, null, null, body.prescriptionId(),
                null, null, draftLines);
        return StockDocumentResponse.from(document, items.findDetailsByDocumentId(document.getId()));
    }

    private void requirePharmacist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.PHARMACIST)) {
            throw new BusinessException("PHARMACIST_INACTIVE", "Tài khoản dược sĩ không hoạt động");
        }
    }
}
