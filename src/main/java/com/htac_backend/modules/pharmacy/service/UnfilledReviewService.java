package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.dto.PrescriptionUnfilledUpdate;
import com.htac_backend.modules.outpatient.service.IssuedPrescriptionService;
import com.htac_backend.modules.outpatient.service.PrescriptionDispensingStateService;
import com.htac_backend.modules.pharmacy.dto.DispensingSnapshotResponse;
import com.htac_backend.modules.pharmacy.dto.ReviewUnfilledRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnfilledReviewService {
    private final IssuedPrescriptionService prescriptions;
    private final PrescriptionDispensingStateService state;
    private final DispensedQuantityService dispensed;
    private final DispenseStockService snapshots;
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;

    public UnfilledReviewService(IssuedPrescriptionService prescriptions,
            PrescriptionDispensingStateService state, DispensedQuantityService dispensed,
            DispenseStockService snapshots, CurrentPharmacistActor actor,
            AccountRoleLookupService accounts) {
        this.prescriptions = prescriptions;
        this.state = state;
        this.dispensed = dispensed;
        this.snapshots = snapshots;
        this.actor = actor;
        this.accounts = accounts;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DispensingSnapshotResponse review(Long prescriptionId, ReviewUnfilledRequest body) {
        requirePharmacist();
        if (body == null || body.items() == null) {
            throw new InvalidRequestException("Cần danh sách phần thuốc chưa nhận");
        }
        var prescription = prescriptions.lockForDispensing(prescriptionId);
        Map<Long, BigDecimal> remaining = new HashMap<>();
        for (var line : prescription.items()) {
            remaining.put(line.id(), line.quantity().subtract(dispensed.postedQuantity(line.id())));
        }
        for (var item : body.items()) {
            if (item == null || item.prescriptionItemId() == null || item.unfilledQuantity() == null) {
                throw new InvalidRequestException("Dòng phần chưa nhận không hợp lệ");
            }
            BigDecimal limit = remaining.get(item.prescriptionItemId());
            if (limit == null || item.unfilledQuantity().compareTo(limit) > 0) {
                throw new InvalidRequestException("Lượng chưa nhận vượt lượng còn có thể cấp");
            }
        }
        state.recordReview(prescriptionId, body.items().stream().map(item ->
                new PrescriptionUnfilledUpdate(item.prescriptionItemId(), item.unfilledQuantity(),
                        item.reason(), item.note())).toList(),
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        return snapshots.snapshot(prescriptionId);
    }

    private void requirePharmacist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.PHARMACIST)) {
            throw new BusinessException("PHARMACIST_INACTIVE", "Tài khoản dược sĩ không hoạt động");
        }
    }
}
