package com.htac_backend.modules.outpatient.dto;

import com.htac_backend.modules.outpatient.entity.MedicalVisit;
import com.htac_backend.modules.outpatient.entity.VisitStatus;
import java.time.Instant;
import java.time.LocalDate;

public record ReceptionVisit(Long id, String visitCode, Long patientId, Long appointmentId, Long slotId,
        Long roomId, LocalDate queueDate, int queueNumber, Instant arrivedAt, VisitStatus status, String reason,
        String outcomeReason) {
    public static ReceptionVisit from(MedicalVisit visit) {
        return new ReceptionVisit(visit.getId(), visit.getVisitCode(), visit.getPatientId(),
                visit.getAppointmentId(), visit.getSlotId(), visit.getRoomId(), visit.getQueueDate(),
                visit.getQueueNumber(), visit.getArrivedAt(), visit.getStatus(), visit.getReason(),
                visit.getOutcomeReason());
    }
}
