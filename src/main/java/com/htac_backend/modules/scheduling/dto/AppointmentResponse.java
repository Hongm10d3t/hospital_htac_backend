package com.htac_backend.modules.scheduling.dto;

import com.htac_backend.modules.scheduling.entity.Appointment;
import com.htac_backend.modules.scheduling.entity.AppointmentStatus;
import java.time.Instant;

public record AppointmentResponse(Long id, String appointmentCode, Long slotId, AppointmentStatus status,
        Instant confirmedAt, Instant closedAt, String nonCompletionReason) {
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(appointment.getId(), appointment.getAppointmentCode(),
                appointment.getSlotId(), appointment.getStatus(), appointment.getConfirmedAt(),
                appointment.getClosedAt(), appointment.getNonCompletionReason());
    }
}
