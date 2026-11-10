package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.dto.CreatePrescriptionRequest;
import com.htac_backend.modules.outpatient.dto.PrescriptionLineRequest;
import com.htac_backend.modules.outpatient.dto.PrescriptionLineResponse;
import com.htac_backend.modules.outpatient.dto.PrescriptionResponse;
import com.htac_backend.modules.outpatient.entity.MedicalVisit;
import com.htac_backend.modules.outpatient.entity.Prescription;
import com.htac_backend.modules.outpatient.entity.PrescriptionItem;
import com.htac_backend.modules.outpatient.entity.PrescriptionStatus;
import com.htac_backend.modules.outpatient.entity.VisitStatus;
import com.htac_backend.modules.outpatient.repository.MedicalVisitRepository;
import com.htac_backend.modules.outpatient.repository.PrescriptionItemRepository;
import com.htac_backend.modules.outpatient.repository.PrescriptionRepository;
import com.htac_backend.modules.pharmacy.service.MedicineDisplayLookupService;
import com.htac_backend.modules.pharmacy.service.PrescribableMedicineService;
import com.htac_backend.modules.scheduling.service.DoctorSlotReadService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorPrescriptionService {
    private final CurrentDoctorActor actor;
    private final AccountRoleLookupService accounts;
    private final DoctorSlotReadService slots;
    private final MedicalVisitRepository visits;
    private final PrescriptionRepository prescriptions;
    private final PrescriptionItemRepository items;
    private final PrescribableMedicineService activeMedicines;
    private final MedicineDisplayLookupService labels;

    public DoctorPrescriptionService(CurrentDoctorActor actor, AccountRoleLookupService accounts,
            DoctorSlotReadService slots, MedicalVisitRepository visits,
            PrescriptionRepository prescriptions, PrescriptionItemRepository items,
            PrescribableMedicineService activeMedicines, MedicineDisplayLookupService labels) {
        this.actor = actor;
        this.accounts = accounts;
        this.slots = slots;
        this.visits = visits;
        this.prescriptions = prescriptions;
        this.items = items;
        this.activeMedicines = activeMedicines;
        this.labels = labels;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PrescriptionResponse create(Long visitId, CreatePrescriptionRequest request) {
        Long doctorId = requireDoctor();
        PrescriptionLineValidator.validate(request.items());
        MedicalVisit visit = lockAssignedVisit(visitId, doctorId);
        requirePrescribable(visit);
        activeMedicines.requireActive(request.items().stream().map(PrescriptionLineRequest::medicineId).toList());
        String code = "RX" + UUID.randomUUID().toString().replace("-", "").substring(0, 23);
        Prescription created = prescriptions.saveAndFlush(new Prescription(code, visitId,
                doctorId, blankToNull(request.note())));
        items.saveAllAndFlush(request.items().stream().map(item -> new PrescriptionItem(created.getId(),
                item.medicineId(), item.quantity(), item.usageInstruction().trim())).toList());
        return response(created);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PrescriptionResponse issue(Long prescriptionId) {
        Long doctorId = requireDoctor();
        Long visitId = prescriptions.findVisitIdById(prescriptionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn thuốc"));
        MedicalVisit visit = lockAssignedVisit(visitId, doctorId);
        Prescription prescription = prescriptions.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn thuốc"));
        requireOwn(prescription, doctorId);
        if (prescription.getStatus() == PrescriptionStatus.ISSUED) {
            return response(prescription);
        }
        if (prescription.getStatus() != PrescriptionStatus.DRAFT) {
            throw new BusinessException("PRESCRIPTION_FINAL", "Đơn thuốc đã có kết quả cuối");
        }
        requirePrescribable(visit);
        List<PrescriptionItem> lines = items.findByPrescriptionIdOrderById(prescriptionId);
        if (lines.isEmpty()) {
            throw new BusinessException("PRESCRIPTION_EMPTY", "Đơn thuốc chưa có thuốc");
        }
        activeMedicines.requireActive(lines.stream().map(PrescriptionItem::getMedicineId).toList());
        prescription.issue(Instant.now().truncatedTo(ChronoUnit.MICROS));
        prescriptions.flush();
        return response(prescription);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public PrescriptionResponse get(Long prescriptionId) {
        Long doctorId = requireDoctor();
        Prescription prescription = prescriptions.findById(prescriptionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn thuốc"));
        requireOwn(prescription, doctorId);
        assignedVisit(prescription.getVisitId(), doctorId);
        return response(prescription);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public List<PrescriptionResponse> forVisit(Long visitId) {
        Long doctorId = requireDoctor();
        assignedVisit(visitId, doctorId);
        return prescriptions.findByVisitIdOrderById(visitId).stream()
                .filter(prescription -> prescription.getDoctorId().equals(doctorId))
                .map(this::response).toList();
    }

    private MedicalVisit lockAssignedVisit(Long visitId, Long doctorId) {
        Long slotId = visits.findSlotIdById(visitId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
        slots.requireAssignedForUpdate(slotId, doctorId);
        return visits.findByIdForUpdate(visitId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
    }

    private MedicalVisit assignedVisit(Long visitId, Long doctorId) {
        MedicalVisit visit = visits.findById(visitId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
        slots.requireAssigned(visit.getSlotId(), doctorId);
        return visit;
    }

    private void requirePrescribable(MedicalVisit visit) {
        if (visit.getStatus() != VisitStatus.IN_PROGRESS && visit.getStatus() != VisitStatus.COMPLETED) {
            throw new BusinessException("VISIT_NOT_PRESCRIBABLE", "Lượt chưa khám hoặc đã bỏ dở");
        }
    }

    private void requireOwn(Prescription prescription, Long doctorId) {
        if (!prescription.getDoctorId().equals(doctorId)) {
            throw new NotFoundException("Không tìm thấy đơn thuốc");
        }
    }

    private PrescriptionResponse response(Prescription prescription) {
        List<PrescriptionItem> lines = items.findByPrescriptionIdOrderById(prescription.getId());
        var medicineLabels = labels.labels(lines.stream().map(PrescriptionItem::getMedicineId).toList());
        return new PrescriptionResponse(prescription.getId(), prescription.getPrescriptionCode(),
                prescription.getVisitId(), prescription.getDoctorId(), prescription.getStatus(),
                prescription.getIssuedAt(), prescription.getNote(), prescription.getDispensingCheckedAt(),
                lines.stream().map(line -> PrescriptionLineResponse.from(line,
                        medicineLabels.get(line.getMedicineId()))).toList());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long requireDoctor() {
        Long doctorId = actor.userId();
        if (!accounts.hasActiveRole(doctorId, AccountRole.DOCTOR)) {
            throw new BusinessException("DOCTOR_INACTIVE", "Tài khoản bác sĩ không hoạt động");
        }
        return doctorId;
    }
}
