package com.htac_backend.modules.outpatient.dto;

import com.htac_backend.modules.outpatient.entity.MedicalVisit;
import com.htac_backend.modules.outpatient.entity.VisitStatus;
import java.time.Instant;
import java.time.LocalDate;

public record PatientVisitSummary(Long id, String visitCode, Long appointmentId, Long slotId,
        Long roomId, LocalDate queueDate, int queueNumber, Instant arrivedAt, VisitStatus status,
        String diagnosis, LocalDate followUpDate) {
    public static PatientVisitSummary from(MedicalVisit visit) {
        boolean finalResult = visit.getStatus() == VisitStatus.COMPLETED;
        return new PatientVisitSummary(visit.getId(), visit.getVisitCode(), visit.getAppointmentId(),
                visit.getSlotId(), visit.getRoomId(), visit.getQueueDate(), visit.getQueueNumber(),
                visit.getArrivedAt(), visit.getStatus(), finalResult ? visit.getDiagnosis() : null,
                finalResult ? visit.getFollowUpDate() : null);
    }
}
