package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.outpatient.dto.PrescriptionUnfilledUpdate;
import com.htac_backend.modules.outpatient.entity.PrescriptionStatus;
import com.htac_backend.modules.outpatient.repository.PrescriptionItemRepository;
import com.htac_backend.modules.outpatient.repository.PrescriptionRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M05-owned write contract; M06 caller holds the prescription row lock. */
@Service
public class PrescriptionDispensingStateService {
    private static final Set<String> REASONS = Set.of("STOCKOUT", "DECLINED", "EXTERNAL", "OTHER");
    private final PrescriptionRepository prescriptions;
    private final PrescriptionItemRepository items;

    public PrescriptionDispensingStateService(PrescriptionRepository prescriptions,
            PrescriptionItemRepository items) {
        this.prescriptions = prescriptions;
        this.items = items;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReview(Long prescriptionId, List<PrescriptionUnfilledUpdate> updates,
            Instant checkedAt) {
        var prescription = prescriptions.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn thuốc"));
        if (prescription.getStatus() != PrescriptionStatus.ISSUED) {
            throw new BusinessException("PRESCRIPTION_NOT_ISSUED", "Chỉ đơn đã phát hành được xử lý");
        }
        var lines = items.findByPrescriptionIdOrderById(prescriptionId);
        if (updates == null || updates.size() != lines.size()) {
            throw new InvalidRequestException("Cần cập nhật đủ các dòng đơn");
        }
        Map<Long, PrescriptionUnfilledUpdate> byId = new HashMap<>();
        for (var update : updates) {
            if (update == null || update.prescriptionItemId() == null
                    || byId.putIfAbsent(update.prescriptionItemId(), update) != null) {
                throw new InvalidRequestException("Dòng đơn trùng hoặc không hợp lệ");
            }
            validate(update);
        }
        for (var line : lines) {
            var update = byId.get(line.getId());
            if (update == null || update.quantity().compareTo(line.getQuantity()) > 0) {
                throw new InvalidRequestException("Dòng đơn hoặc lượng chưa nhận không hợp lệ");
            }
            line.recordUnfilled(update.quantity(), update.reason(),
                    update.note() == null ? null : update.note().trim());
        }
        prescription.markDispensingChecked(checkedAt);
        items.flush();
        prescriptions.flush();
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(propagation = Propagation.MANDATORY)
    public void reconcileAfterDispense(Long prescriptionId, Map<Long, BigDecimal> remaining,
            Instant checkedAt) {
        var prescription = prescriptions.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn thuốc"));
        if (prescription.getDispensingCheckedAt() == null) {
            return;
        }
        for (var line : items.findByPrescriptionIdOrderById(prescriptionId)) {
            BigDecimal limit = remaining.get(line.getId());
            if (limit != null && line.getUnfilledQuantity().compareTo(limit) > 0) {
                line.recordUnfilled(limit, limit.signum() == 0 ? null : line.getUnfilledReason(),
                        limit.signum() == 0 ? null : line.getUnfilledNote());
            }
        }
        prescription.markDispensingChecked(checkedAt);
        items.flush();
        prescriptions.flush();
    }

    private void validate(PrescriptionUnfilledUpdate update) {
        BigDecimal quantity = update.quantity();
        if (quantity == null || quantity.signum() < 0 || quantity.scale() > 3
                || quantity.precision() - quantity.scale() > 11
                || (update.note() != null && update.note().length() > 2000)) {
            throw new InvalidRequestException("Lượng chưa nhận không hợp lệ");
        }
        if (quantity.signum() == 0) {
            if (update.reason() != null || (update.note() != null && !update.note().isBlank())) {
                throw new InvalidRequestException("Không có phần chưa nhận thì không ghi lý do");
            }
        } else if (update.reason() == null || !REASONS.contains(update.reason())
                || ("OTHER".equals(update.reason())
                && (update.note() == null || update.note().isBlank()))) {
            throw new InvalidRequestException("Cần lý do phần chưa nhận");
        }
    }
}
