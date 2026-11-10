package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.dto.DoctorVisitResponse;
import com.htac_backend.modules.outpatient.dto.PatientVisitDetail;
import com.htac_backend.modules.outpatient.dto.PatientVisitSummary;
import com.htac_backend.modules.outpatient.entity.MedicalVisit;
import com.htac_backend.modules.outpatient.entity.VisitStatus;
import com.htac_backend.modules.outpatient.repository.MedicalVisitRepository;
import com.htac_backend.modules.reception.service.ClinicalPatientIdentityService;
import com.htac_backend.modules.scheduling.dto.ClinicSlotResponse;
import com.htac_backend.modules.scheduling.service.DoctorSlotReadService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorVisitService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CurrentDoctorActor actor;
    private final AccountRoleLookupService accounts;
    private final DoctorSlotReadService slots;
    private final MedicalVisitRepository visits;
    private final ClinicalPatientIdentityService patients;
    private final PatientHistoryReadService history;

    public DoctorVisitService(CurrentDoctorActor actor, AccountRoleLookupService accounts,
            DoctorSlotReadService slots, MedicalVisitRepository visits,
            ClinicalPatientIdentityService patients, PatientHistoryReadService history) {
        this.actor = actor;
        this.accounts = accounts;
        this.slots = slots;
        this.visits = visits;
        this.patients = patients;
        this.history = history;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public PageResponse<ClinicSlotResponse> slots(LocalDate date, PageQuery page) {
        Long doctorId = requireDoctor();
        return slots.assigned(doctorId, date == null ? LocalDate.now(HOSPITAL_ZONE) : date, page);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public PageResponse<DoctorVisitResponse> waiting(Long slotId, PageQuery page) {
        Long doctorId = requireDoctor();
        slots.requireAssigned(slotId, doctorId);
        var found = visits.findBySlotIdAndStatus(slotId, VisitStatus.WAITING, page.toPageRequest());
        Map<Long, com.htac_backend.modules.reception.dto.ClinicalPatientIdentity> identities =
                patients.forPatients(found.getContent().stream().map(MedicalVisit::getPatientId).toList());
        return PageResponse.from(found.map(visit -> DoctorVisitResponse.from(visit,
                identities.get(visit.getPatientId()))));
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public DoctorVisitResponse get(Long visitId) {
        Long doctorId = requireDoctor();
        MedicalVisit visit = requireAssignedVisit(visitId, doctorId);
        return response(visit);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DoctorVisitResponse start(Long visitId) {
        Long doctorId = requireDoctor();
        Long slotId = visits.findSlotIdById(visitId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
        slots.requireAssignedForUpdate(slotId, doctorId);
        MedicalVisit visit = visits.findByIdForUpdate(visitId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
        if (visit.getStatus() == VisitStatus.WAITING) {
            visit.start(Instant.now().truncatedTo(ChronoUnit.MICROS));
            visits.flush();
        } else if (visit.getStatus() != VisitStatus.IN_PROGRESS) {
            throw new BusinessException("VISIT_NOT_STARTABLE", "Lượt không còn chờ khám");
        }
        return response(visit);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public PageResponse<PatientVisitSummary> patientHistory(Long currentVisitId, PageQuery page) {
        Long doctorId = requireDoctor();
        MedicalVisit current = requireActiveAssignedVisit(currentVisitId, doctorId);
        return history.visits(current.getPatientId(), null, null, page);
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public PatientVisitDetail patientHistoryDetail(Long currentVisitId, Long pastVisitId) {
        Long doctorId = requireDoctor();
        MedicalVisit current = requireActiveAssignedVisit(currentVisitId, doctorId);
        return history.visit(current.getPatientId(), pastVisitId);
    }

    private MedicalVisit requireActiveAssignedVisit(Long visitId, Long doctorId) {
        MedicalVisit visit = requireAssignedVisit(visitId, doctorId);
        if (visit.getStatus() != VisitStatus.WAITING && visit.getStatus() != VisitStatus.IN_PROGRESS) {
            throw new NotFoundException("Không tìm thấy lượt đang được phân công");
        }
        return visit;
    }

    private MedicalVisit requireAssignedVisit(Long visitId, Long doctorId) {
        MedicalVisit visit = visits.findById(visitId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
        slots.requireAssigned(visit.getSlotId(), doctorId);
        return visit;
    }

    private DoctorVisitResponse response(MedicalVisit visit) {
        return DoctorVisitResponse.from(visit, patients.get(visit.getPatientId()));
    }

    private Long requireDoctor() {
        Long doctorId = actor.userId();
        if (!accounts.hasActiveRole(doctorId, AccountRole.DOCTOR)) {
            throw new BusinessException("DOCTOR_INACTIVE", "Tài khoản bác sĩ không hoạt động");
        }
        return doctorId;
    }
}
