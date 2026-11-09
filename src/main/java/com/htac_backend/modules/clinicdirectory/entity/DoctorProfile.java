package com.htac_backend.modules.clinicdirectory.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctor_profile")
public class DoctorProfile extends AuditedEntity {
    @Id @Column(name = "user_id")
    private Long userId;
    @Column(name = "specialty_id", nullable = false)
    private Long specialtyId;
    @Column(name = "is_public", nullable = false)
    private boolean publicProfile = false;

    protected DoctorProfile() { }

    public DoctorProfile(Long userId, Long specialtyId) {
        this.userId = userId;
        this.specialtyId = specialtyId;
    }

    public Long getUserId() { return userId; }
    public Long getSpecialtyId() { return specialtyId; }
}
