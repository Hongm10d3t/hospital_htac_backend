package com.htac_backend.modules.scheduling.entity;

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

@Entity
@Table(name = "appointment")
public class Appointment extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "appointment_code", nullable = false, length = 30)
    private String appointmentCode;
    @Column(name = "patient_id", nullable = false)
    private Long patientId;
    @Column(name = "slot_id", nullable = false)
    private Long slotId;
    @Column(nullable = false, columnDefinition = "text")
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.PENDING;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "confirmed_by")
    private Long confirmedBy;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "non_completion_reason", columnDefinition = "text")
    private String nonCompletionReason;

    protected Appointment() { }

    public Appointment(String appointmentCode, Long patientId, Long slotId, String reason) {
        this.appointmentCode = appointmentCode;
        this.patientId = patientId;
        this.slotId = slotId;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public String getAppointmentCode() { return appointmentCode; }
    public Long getPatientId() { return patientId; }
    public Long getSlotId() { return slotId; }
    public String getReason() { return reason; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Long getConfirmedBy() { return confirmedBy; }
    public Instant getClosedAt() { return closedAt; }
    public String getNonCompletionReason() { return nonCompletionReason; }

    public void confirm(Long confirmerId, Instant now) {
        status = AppointmentStatus.CONFIRMED;
        confirmedBy = confirmerId;
        confirmedAt = now;
    }

    public void complete(Instant now) {
        status = AppointmentStatus.COMPLETED;
        closedAt = now;
    }

    public void notComplete(String reason, Instant now) {
        status = AppointmentStatus.NOT_COMPLETED;
        nonCompletionReason = reason;
        closedAt = now;
    }
}
