package com.htac_backend.modules.scheduling.dto;

import com.htac_backend.modules.scheduling.entity.Appointment;
import com.htac_backend.modules.scheduling.entity.AppointmentStatus;
import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import java.time.Instant;

public record AppointmentResponse(Long id, String appointmentCode, Long slotId, Long doctorId,
        Long roomId, Instant startAt, Instant endAt, AppointmentStatus status,
        Instant confirmedAt, Instant closedAt, String nonCompletionReason) {
    public static AppointmentResponse from(Appointment appointment, ClinicSlot slot) {
        return new AppointmentResponse(appointment.getId(), appointment.getAppointmentCode(),
                appointment.getSlotId(), slot.getDoctorId(), slot.getRoomId(), slot.getStartAt(),
                slot.getEndAt(), appointment.getStatus(), appointment.getConfirmedAt(),
                appointment.getClosedAt(), appointment.getNonCompletionReason());
    }
}
