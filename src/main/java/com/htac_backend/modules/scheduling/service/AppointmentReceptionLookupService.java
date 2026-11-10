package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.scheduling.dto.ReceptionBooking;
import com.htac_backend.modules.scheduling.entity.Appointment;
import com.htac_backend.modules.scheduling.entity.AppointmentStatus;
import com.htac_backend.modules.scheduling.repository.AppointmentRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentReceptionLookupService {
    private final AppointmentRepository appointments;

    public AppointmentReceptionLookupService(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Long slotId(Long appointmentId) {
        return appointments.findSlotIdById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
    }

    /** Called after the shared slot lock has been acquired. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ReceptionBooking requireHeld(Long appointmentId) {
        Appointment appointment = appointments.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("APPOINTMENT_NOT_HELD", "Lịch hẹn không còn giữ suất hợp lệ");
        }
        return new ReceptionBooking(appointment.getId(), appointment.getPatientId(),
                appointment.getSlotId(), appointment.getReason());
    }
}
