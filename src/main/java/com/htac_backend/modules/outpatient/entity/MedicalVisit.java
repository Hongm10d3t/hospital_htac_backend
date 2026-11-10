package com.htac_backend.modules.outpatient.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "medical_visit")
public class MedicalVisit extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "visit_code", nullable = false, length = 30)
    private String visitCode;
    @Column(name = "patient_id", nullable = false)
    private Long patientId;
    @Column(name = "appointment_id")
    private Long appointmentId;
    @Column(name = "slot_id", nullable = false)
    private Long slotId;
    @Column(name = "room_id", nullable = false)
    private Long roomId;
    @Column(name = "queue_date", nullable = false)
    private LocalDate queueDate;
    @Column(name = "queue_number", nullable = false)
    private int queueNumber;
    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status = VisitStatus.WAITING;
    @Column(columnDefinition = "text")
    private String reason;
    @Column(name = "clinical_notes", columnDefinition = "text")
    private String clinicalNotes;
    @Column(name = "diagnosis_code", length = 20)
    private String diagnosisCode;
    @Column(columnDefinition = "text")
    private String diagnosis;
    @Column(name = "treatment_plan", columnDefinition = "text")
    private String treatmentPlan;
    @Column(name = "follow_up_date")
    private LocalDate followUpDate;
    @Column(name = "outcome_reason", columnDefinition = "text")
    private String outcomeReason;

    protected MedicalVisit() { }

    public MedicalVisit(String visitCode, Long patientId, Long appointmentId, Long slotId, Long roomId,
            LocalDate queueDate, int queueNumber, Instant arrivedAt, String reason) {
        this.visitCode = visitCode;
        this.patientId = patientId;
        this.appointmentId = appointmentId;
        this.slotId = slotId;
        this.roomId = roomId;
        this.queueDate = queueDate;
        this.queueNumber = queueNumber;
        this.arrivedAt = arrivedAt;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public String getVisitCode() { return visitCode; }
    public Long getPatientId() { return patientId; }
    public Long getAppointmentId() { return appointmentId; }
    public Long getSlotId() { return slotId; }
    public Long getRoomId() { return roomId; }
    public LocalDate getQueueDate() { return queueDate; }
    public int getQueueNumber() { return queueNumber; }
    public Instant getArrivedAt() { return arrivedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public VisitStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getClinicalNotes() { return clinicalNotes; }
    public String getDiagnosisCode() { return diagnosisCode; }
    public String getDiagnosis() { return diagnosis; }
    public String getTreatmentPlan() { return treatmentPlan; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public String getOutcomeReason() { return outcomeReason; }

    public void start(Instant now) {
        status = VisitStatus.IN_PROGRESS;
        startedAt = now;
    }

    public void notComplete(String reason) {
        status = VisitStatus.NOT_COMPLETED;
        outcomeReason = reason;
    }

    public void voidReception(String reason) {
        status = VisitStatus.VOIDED;
        outcomeReason = reason;
    }
}
