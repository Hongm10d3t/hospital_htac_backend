package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.dto.VisitOutcome;
import com.htac_backend.modules.outpatient.service.VisitOutcomeReadService;
import com.htac_backend.modules.reception.dto.AppointmentPatientContact;
import com.htac_backend.modules.reception.service.AppointmentPatientContactService;
import com.htac_backend.modules.scheduling.dto.AppointmentResponse;
import com.htac_backend.modules.scheduling.dto.ReceptionAppointmentResponse;
import com.htac_backend.modules.scheduling.entity.Appointment;
import com.htac_backend.modules.scheduling.entity.AppointmentStatus;
import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import com.htac_backend.modules.scheduling.repository.AppointmentRepository;
import com.htac_backend.modules.scheduling.repository.ClinicSlotRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceptionAppointmentService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CurrentSchedulingActor actor;
    private final AccountRoleLookupService accounts;
    private final AppointmentRepository appointments;
    private final ClinicSlotRepository slots;
    private final AppointmentPatientContactService contacts;
    private final VisitOutcomeReadService visits;

    public ReceptionAppointmentService(CurrentSchedulingActor actor, AccountRoleLookupService accounts,
            AppointmentRepository appointments, ClinicSlotRepository slots,
            AppointmentPatientContactService contacts, VisitOutcomeReadService visits) {
        this.actor = actor;
        this.accounts = accounts;
        this.appointments = appointments;
        this.slots = slots;
        this.contacts = contacts;
        this.visits = visits;
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(readOnly = true)
    public PageResponse<ReceptionAppointmentResponse> list(AppointmentStatus status, LocalDate date,
            PageQuery page) {
        Instant from = date == null ? null : date.atStartOfDay(HOSPITAL_ZONE).toInstant();
        Instant to = date == null ? null : date.plusDays(1).atStartOfDay(HOSPITAL_ZONE).toInstant();
        var result = appointments.searchReception(status, from, to, page.toPageRequest());
        Map<Long, ClinicSlot> slotMap = slots.findAllById(result.getContent().stream()
                .map(Appointment::getSlotId).distinct().toList()).stream()
                .collect(Collectors.toMap(ClinicSlot::getId, Function.identity()));
        Map<Long, AppointmentPatientContact> contactMap = contacts.forPatients(result.getContent().stream()
                .map(Appointment::getPatientId).distinct().toList());
        return PageResponse.from(result.map(appointment -> response(appointment,
                slotMap.get(appointment.getSlotId()), contactMap.get(appointment.getPatientId()))));
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(readOnly = true)
    public ReceptionAppointmentResponse get(Long id) {
        Appointment appointment = appointments.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
        ClinicSlot slot = slots.findById(appointment.getSlotId()).orElseThrow();
        return response(appointment, slot, contacts.forPatient(appointment.getPatientId()));
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceptionAppointmentResponse confirm(Long id) {
        Long confirmerId = requireActiveReceptionist();
        Appointment appointment = lock(id);
        if (appointment.getStatus() == AppointmentStatus.PENDING) {
            appointment.confirm(confirmerId, Instant.now());
            appointments.flush();
        } else if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw finalState();
        }
        return getLockedResponse(appointment);
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceptionAppointmentResponse notComplete(Long id, String reason) {
        requireActiveReceptionist();
        if (reason == null || reason.isBlank() || reason.length() > 3000) {
            throw new InvalidRequestException("Lý do không hoàn thành không hợp lệ");
        }
        Appointment appointment = lock(id);
        String cleaned = reason.trim();
        if (appointment.getStatus() == AppointmentStatus.NOT_COMPLETED) {
            if (!cleaned.equals(appointment.getNonCompletionReason())) {
                throw finalState();
            }
            return getLockedResponse(appointment);
        }
        requirePendingOrConfirmed(appointment);
        if (visits.nonVoidedForAppointment(id).isPresent()) {
            throw new BusinessException("APPOINTMENT_HAS_VISIT",
                    "Lịch đã có lượt thực tế, cần xử lý kết quả từ lượt khám");
        }
        appointment.notComplete(cleaned, Instant.now());
        appointments.flush();
        return getLockedResponse(appointment);
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceptionAppointmentResponse complete(Long id) {
        requireActiveReceptionist();
        Appointment appointment = lock(id);
        completeIfVisitCompleted(appointment);
        return getLockedResponse(appointment);
    }

    /** M05 calls this in its READ_COMMITTED transaction after completing the visit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeFromVisit(Long id) {
        completeIfVisitCompleted(lock(id));
    }

    /** M05 calls this in its READ_COMMITTED transaction after marking a visit NOT_COMPLETED. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notCompleteFromVisit(Long id) {
        Appointment appointment = lock(id);
        VisitOutcome visit = requiredMatchingVisit(appointment);
        if (!"NOT_COMPLETED".equals(visit.status()) || visit.outcomeReason() == null
                || visit.outcomeReason().isBlank()) {
            throw new BusinessException("VISIT_OUTCOME_REQUIRED", "Lượt khám chưa có kết quả không hoàn thành");
        }
        if (appointment.getStatus() == AppointmentStatus.NOT_COMPLETED
                && visit.outcomeReason().equals(appointment.getNonCompletionReason())) {
            return;
        }
        requirePendingOrConfirmed(appointment);
        appointment.notComplete(visit.outcomeReason(), Instant.now());
        appointments.flush();
    }

    private void completeIfVisitCompleted(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.NOT_COMPLETED) {
            throw finalState();
        }
        VisitOutcome visit = requiredMatchingVisit(appointment);
        if (!"COMPLETED".equals(visit.status())) {
            throw new BusinessException("VISIT_NOT_COMPLETED", "Lượt khám chưa hoàn thành");
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            return;
        }
        requirePendingOrConfirmed(appointment);
        appointment.complete(Instant.now());
        appointments.flush();
    }

    private VisitOutcome requiredMatchingVisit(Appointment appointment) {
        VisitOutcome visit = visits.nonVoidedForAppointment(appointment.getId())
                .orElseThrow(() -> new BusinessException("VISIT_REQUIRED", "Lịch chưa có lượt thực tế"));
        if (!visit.patientId().equals(appointment.getPatientId())
                || !visit.slotId().equals(appointment.getSlotId())) {
            throw new BusinessException("VISIT_MISMATCH", "Lượt khám không khớp lịch hẹn");
        }
        return visit;
    }

    private Appointment lock(Long id) {
        Long slotId = appointments.findSlotIdById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
        slots.findByIdForUpdate(slotId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khung khám"));
        return appointments.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
    }

    private Long requireActiveReceptionist() {
        Long accountId = actor.userId();
        if (!accounts.hasActiveRole(accountId, AccountRole.RECEPTIONIST)) {
            throw new BusinessException("RECEPTIONIST_INACTIVE", "Tài khoản tiếp đón không hoạt động");
        }
        return accountId;
    }

    private void requirePendingOrConfirmed(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw finalState();
        }
    }

    private BusinessException finalState() {
        return new BusinessException("APPOINTMENT_FINAL", "Lịch hẹn đã có kết quả cuối");
    }

    private ReceptionAppointmentResponse getLockedResponse(Appointment appointment) {
        ClinicSlot slot = slots.findById(appointment.getSlotId()).orElseThrow();
        return response(appointment, slot, contacts.forPatient(appointment.getPatientId()));
    }

    private ReceptionAppointmentResponse response(Appointment appointment, ClinicSlot slot,
            AppointmentPatientContact contact) {
        return new ReceptionAppointmentResponse(AppointmentResponse.from(appointment, slot),
                appointment.getPatientId(), contact.fullName(), contact.phone(), appointment.getReason());
    }
}
