package com.htac_backend.modules.outpatient.dto;

import com.htac_backend.modules.outpatient.entity.MedicalVisit;
import com.htac_backend.modules.outpatient.entity.VisitStatus;
import com.htac_backend.modules.reception.dto.ClinicalPatientIdentity;
import java.time.Instant;
import java.time.LocalDate;

public record DoctorVisitResponse(Long id, String visitCode, ClinicalPatientIdentity patient,
        Long appointmentId, Long slotId, Long roomId, LocalDate queueDate, int queueNumber,
        Instant arrivedAt, Instant startedAt, Instant completedAt, VisitStatus status,
        String reason, String clinicalNotes, String diagnosisCode, String diagnosis,
        String treatmentPlan, LocalDate followUpDate, String outcomeReason) {
    public static DoctorVisitResponse from(MedicalVisit visit, ClinicalPatientIdentity patient) {
        return new DoctorVisitResponse(visit.getId(), visit.getVisitCode(), patient,
                visit.getAppointmentId(), visit.getSlotId(), visit.getRoomId(), visit.getQueueDate(),
                visit.getQueueNumber(), visit.getArrivedAt(), visit.getStartedAt(),
                visit.getCompletedAt(), visit.getStatus(), visit.getReason(),
                visit.getClinicalNotes(), visit.getDiagnosisCode(), visit.getDiagnosis(),
                visit.getTreatmentPlan(), visit.getFollowUpDate(), visit.getOutcomeReason());
    }
}
