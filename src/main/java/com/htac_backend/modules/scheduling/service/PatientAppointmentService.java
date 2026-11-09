package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.reception.service.PatientAccountLookupService;
import com.htac_backend.modules.scheduling.dto.AppointmentResponse;
import com.htac_backend.modules.scheduling.entity.Appointment;
import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import com.htac_backend.modules.scheduling.repository.AppointmentRepository;
import com.htac_backend.modules.scheduling.repository.ClinicSlotRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('PATIENT')")
public class PatientAppointmentService {
    private final CurrentSchedulingActor actor;
    private final AccountRoleLookupService accounts;
    private final PatientAccountLookupService patients;
    private final AppointmentRepository appointments;
    private final ClinicSlotRepository slots;

    public PatientAppointmentService(CurrentSchedulingActor actor, AccountRoleLookupService accounts,
            PatientAccountLookupService patients, AppointmentRepository appointments, ClinicSlotRepository slots) {
        this.actor = actor;
        this.accounts = accounts;
        this.patients = patients;
        this.appointments = appointments;
        this.slots = slots;
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> ownList(PageQuery page) {
        Long patientId = ownPatientId();
        var result = appointments.findByPatientId(patientId, page.toPageRequest());
        Map<Long, ClinicSlot> slotMap = slots.findAllById(result.getContent().stream()
                .map(Appointment::getSlotId).distinct().toList()).stream()
                .collect(Collectors.toMap(ClinicSlot::getId, Function.identity()));
        return PageResponse.from(result.map(appointment -> AppointmentResponse.from(appointment,
                slotMap.get(appointment.getSlotId()))));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse ownGet(Long id) {
        Long patientId = ownPatientId();
        Appointment appointment = appointments.findByIdAndPatientId(id, patientId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
        ClinicSlot slot = slots.findById(appointment.getSlotId()).orElseThrow();
        return AppointmentResponse.from(appointment, slot);
    }

    private Long ownPatientId() {
        Long accountId = actor.userId();
        if (!accounts.hasActiveRole(accountId, AccountRole.PATIENT)) {
            throw new BusinessException("PATIENT_ACCOUNT_INACTIVE", "Tài khoản bệnh nhân không hoạt động");
        }
        return patients.requirePatientId(accountId);
    }
}
