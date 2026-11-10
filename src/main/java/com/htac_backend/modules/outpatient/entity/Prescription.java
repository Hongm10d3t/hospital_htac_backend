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

@Entity
@Table(name = "prescription")
public class Prescription extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "prescription_code", nullable = false, length = 30)
    private String prescriptionCode;
    @Column(name = "visit_id", nullable = false)
    private Long visitId;
    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;
    @Column(name = "issued_at")
    private Instant issuedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private PrescriptionStatus status = PrescriptionStatus.DRAFT;
    @Column(columnDefinition = "text")
    private String note;
    @Column(name = "dispensing_checked_at")
    private Instant dispensingCheckedAt;

    protected Prescription() { }

    public Prescription(String code, Long visitId, Long doctorId, String note) {
        this.prescriptionCode = code;
        this.visitId = visitId;
        this.doctorId = doctorId;
        this.note = note;
    }

    public Long getId() { return id; }
    public String getPrescriptionCode() { return prescriptionCode; }
    public Long getVisitId() { return visitId; }
    public Long getDoctorId() { return doctorId; }
    public Instant getIssuedAt() { return issuedAt; }
    public PrescriptionStatus getStatus() { return status; }
    public String getNote() { return note; }
    public Instant getDispensingCheckedAt() { return dispensingCheckedAt; }

    public void issue(Instant now) {
        status = PrescriptionStatus.ISSUED;
        issuedAt = now;
    }

    public void replaceNote(String note) { this.note = note; }

    public void cancel(String reason) {
        status = PrescriptionStatus.CANCELLED;
        note = note == null ? "Lý do hủy: " + reason : note + "\nLý do hủy: " + reason;
    }
}
