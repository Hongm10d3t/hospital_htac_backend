package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.dto.DoctorVisitResponse;
import com.htac_backend.modules.outpatient.dto.UpdateClinicalVisitRequest;
import com.htac_backend.modules.outpatient.entity.MedicalVisit;
import com.htac_backend.modules.outpatient.entity.VisitStatus;
import com.htac_backend.modules.outpatient.repository.MedicalVisitRepository;
import com.htac_backend.modules.reception.service.ClinicalPatientIdentityService;
import com.htac_backend.modules.scheduling.service.DoctorSlotReadService;
import com.htac_backend.modules.scheduling.service.ReceptionAppointmentService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalVisitService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CurrentDoctorActor actor;
    private final AccountRoleLookupService accounts;
    private final DoctorSlotReadService slots;
    private final MedicalVisitRepository visits;
    private final ClinicalPatientIdentityService patients;
    private final ReceptionAppointmentService appointments;

    public ClinicalVisitService(CurrentDoctorActor actor, AccountRoleLookupService accounts,
            DoctorSlotReadService slots, MedicalVisitRepository visits,
            ClinicalPatientIdentityService patients, ReceptionAppointmentService appointments) {
        this.actor = actor;
        this.accounts = accounts;
        this.slots = slots;
        this.visits = visits;
        this.patients = patients;
        this.appointments = appointments;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DoctorVisitResponse update(Long visitId, UpdateClinicalVisitRequest body) {
        MedicalVisit visit = lockAssigned(visitId);
        if (visit.getStatus() != VisitStatus.IN_PROGRESS) {
            throw new BusinessException("VISIT_NOT_IN_PROGRESS", "Lượt chưa bắt đầu hoặc đã kết thúc");
        }
        if (body.followUpDate() != null && body.followUpDate().isBefore(LocalDate.now(HOSPITAL_ZONE))) {
            throw new InvalidRequestException("Ngày tái khám không được ở quá khứ");
        }
        visit.updateClinical(blankToNull(body.clinicalNotes()), blankToNull(body.diagnosisCode()),
                blankToNull(body.diagnosis()), blankToNull(body.treatmentPlan()), body.followUpDate());
        visits.flush();
        return response(visit);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DoctorVisitResponse complete(Long visitId) {
        MedicalVisit visit = lockAssigned(visitId);
        if (visit.getStatus() == VisitStatus.COMPLETED) {
            return response(visit);
        }
        if (visit.getStatus() != VisitStatus.IN_PROGRESS) {
            throw new BusinessException("VISIT_NOT_IN_PROGRESS", "Lượt chưa bắt đầu hoặc đã kết thúc");
        }
        if (visit.getDiagnosis() == null || visit.getDiagnosis().isBlank()) {
            throw new BusinessException("DIAGNOSIS_REQUIRED", "Cần ghi chẩn đoán trước khi hoàn tất");
        }
        visit.complete(Instant.now().truncatedTo(ChronoUnit.MICROS));
        visits.flush();
        if (visit.getAppointmentId() != null) {
            appointments.completeFromVisit(visit.getAppointmentId());
        }
        return response(visit);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DoctorVisitResponse notComplete(Long visitId, String reason) {
        String cleaned = cleanReason(reason);
        MedicalVisit visit = lockAssigned(visitId);
        if (visit.getStatus() == VisitStatus.NOT_COMPLETED
                && Objects.equals(visit.getOutcomeReason(), cleaned)) {
            return response(visit);
        }
        if (visit.getStatus() != VisitStatus.IN_PROGRESS) {
            throw new BusinessException("VISIT_NOT_IN_PROGRESS", "Lượt chưa bắt đầu hoặc đã kết thúc");
        }
        visit.notComplete(cleaned);
        visits.flush();
        if (visit.getAppointmentId() != null) {
            appointments.notCompleteFromVisit(visit.getAppointmentId());
        }
        return response(visit);
    }

    private MedicalVisit lockAssigned(Long visitId) {
        Long doctorId = actor.userId();
        if (!accounts.hasActiveRole(doctorId, AccountRole.DOCTOR)) {
            throw new BusinessException("DOCTOR_INACTIVE", "Tài khoản bác sĩ không hoạt động");
        }
        Long slotId = visits.findSlotIdById(visitId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
        slots.requireAssignedForUpdate(slotId, doctorId);
        return visits.findByIdForUpdate(visitId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
    }

    private DoctorVisitResponse response(MedicalVisit visit) {
        return DoctorVisitResponse.from(visit, patients.get(visit.getPatientId()));
    }

    private String cleanReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 3000) {
            throw new InvalidRequestException("Lý do không hoàn thành không hợp lệ");
        }
        return reason.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
