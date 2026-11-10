package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.dto.PrescriptionLineRequest;
import com.htac_backend.modules.outpatient.dto.PrescriptionResponse;
import com.htac_backend.modules.outpatient.dto.UpdatePrescriptionRequest;
import com.htac_backend.modules.outpatient.entity.Prescription;
import com.htac_backend.modules.outpatient.entity.PrescriptionItem;
import com.htac_backend.modules.outpatient.entity.PrescriptionStatus;
import com.htac_backend.modules.outpatient.repository.MedicalVisitRepository;
import com.htac_backend.modules.outpatient.repository.PrescriptionItemRepository;
import com.htac_backend.modules.outpatient.repository.PrescriptionRepository;
import com.htac_backend.modules.pharmacy.service.PrescribableMedicineService;
import com.htac_backend.modules.pharmacy.service.PrescriptionStockReferenceService;
import com.htac_backend.modules.scheduling.service.DoctorSlotReadService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorPrescriptionAmendmentService {
    private final CurrentDoctorActor actor;
    private final AccountRoleLookupService accounts;
    private final DoctorSlotReadService slots;
    private final MedicalVisitRepository visits;
    private final PrescriptionRepository prescriptions;
    private final PrescriptionItemRepository items;
    private final PrescribableMedicineService medicines;
    private final PrescriptionStockReferenceService stock;
    private final DoctorPrescriptionService responses;

    public DoctorPrescriptionAmendmentService(CurrentDoctorActor actor, AccountRoleLookupService accounts,
            DoctorSlotReadService slots, MedicalVisitRepository visits,
            PrescriptionRepository prescriptions, PrescriptionItemRepository items,
            PrescribableMedicineService medicines, PrescriptionStockReferenceService stock,
            DoctorPrescriptionService responses) {
        this.actor = actor;
        this.accounts = accounts;
        this.slots = slots;
        this.visits = visits;
        this.prescriptions = prescriptions;
        this.items = items;
        this.medicines = medicines;
        this.stock = stock;
        this.responses = responses;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PrescriptionResponse update(Long prescriptionId, UpdatePrescriptionRequest request) {
        PrescriptionLineValidator.validate(request.items());
        Prescription prescription = lockOwn(prescriptionId);
        if (prescription.getStatus() != PrescriptionStatus.DRAFT) {
            throw new BusinessException("PRESCRIPTION_IMMUTABLE", "Chỉ đơn nháp được sửa");
        }
        requireNoStockDocument(prescriptionId);
        medicines.requireActive(request.items().stream().map(PrescriptionLineRequest::medicineId).toList());
        List<PrescriptionItem> previous = items.findByPrescriptionIdOrderById(prescriptionId);
        items.deleteAll(previous);
        items.flush();
        items.saveAllAndFlush(request.items().stream().map(line -> new PrescriptionItem(prescriptionId,
                line.medicineId(), line.quantity(), line.usageInstruction().trim())).toList());
        prescription.replaceNote(blankToNull(request.note()));
        prescriptions.flush();
        return responses.get(prescriptionId);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PrescriptionResponse cancel(Long prescriptionId, String reason) {
        String cleaned = cleanReason(reason);
        Prescription prescription = lockOwn(prescriptionId);
        if (prescription.cancelledForReason(cleaned)) {
            return responses.get(prescriptionId);
        }
        if (prescription.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new BusinessException("PRESCRIPTION_FINAL", "Đơn đã hủy với lý do khác");
        }
        requireNoStockDocument(prescriptionId);
        prescription.cancel(cleaned);
        prescriptions.flush();
        return responses.get(prescriptionId);
    }

    private Prescription lockOwn(Long prescriptionId) {
        Long doctorId = actor.userId();
        if (!accounts.hasActiveRole(doctorId, AccountRole.DOCTOR)) {
            throw new BusinessException("DOCTOR_INACTIVE", "Tài khoản bác sĩ không hoạt động");
        }
        Long visitId = prescriptions.findVisitIdById(prescriptionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn thuốc"));
        var visit = visits.findById(visitId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
        slots.requireAssigned(visit.getSlotId(), doctorId);
        Prescription prescription = prescriptions.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn thuốc"));
        if (!prescription.getDoctorId().equals(doctorId)) {
            throw new NotFoundException("Không tìm thấy đơn thuốc");
        }
        return prescription;
    }

    private void requireNoStockDocument(Long prescriptionId) {
        if (stock.hasActiveDocument(prescriptionId)) {
            throw new BusinessException("PRESCRIPTION_HAS_STOCK_DOCUMENT",
                    "Đơn đã có phiếu kho đang hiệu lực");
        }
    }

    private String cleanReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 3000) {
            throw new InvalidRequestException("Lý do hủy không hợp lệ");
        }
        return reason.trim();
    }

    private String blankToNull(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
