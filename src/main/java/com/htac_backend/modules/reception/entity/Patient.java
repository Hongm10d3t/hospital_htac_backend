package com.htac_backend.modules.reception.entity;

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
@Table(name = "patient")
public class Patient extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "patient_code", nullable = false, length = 30)
    private String patientCode;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "citizen_id", length = 12)
    private String citizenId;
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PatientGender gender;
    @Column(columnDefinition = "text")
    private String address;
    @Column(name = "registration_token_hash", length = 64)
    private String registrationTokenHash;
    @Column(name = "registration_token_expires_at")
    private Instant registrationTokenExpiresAt;

    protected Patient() { }

    public Patient(String patientCode, Long userId, String citizenId, String fullName,
            LocalDate dateOfBirth, PatientGender gender, String address) {
        this.patientCode = patientCode;
        this.userId = userId;
        this.citizenId = citizenId;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.address = address;
    }

    public Long getId() { return id; }
    public String getPatientCode() { return patientCode; }
    public Long getUserId() { return userId; }
    public String getCitizenId() { return citizenId; }
    public String getFullName() { return fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public boolean isLinked() { return userId != null; }

    public void setCitizenId(String citizenId) { this.citizenId = citizenId; }
    public void setRegistrationToken(String hash, Instant expiresAt) {
        this.registrationTokenHash = hash;
        this.registrationTokenExpiresAt = expiresAt;
    }
    public boolean matchesRegistrationToken(String hash, Instant now) {
        return registrationTokenHash != null && registrationTokenExpiresAt != null
                && registrationTokenExpiresAt.isAfter(now)
                && java.security.MessageDigest.isEqual(
                        java.util.HexFormat.of().parseHex(registrationTokenHash),
                        java.util.HexFormat.of().parseHex(hash));
    }
    public void link(Long userId) {
        this.userId = userId;
        this.registrationTokenHash = null;
        this.registrationTokenExpiresAt = null;
    }
}
