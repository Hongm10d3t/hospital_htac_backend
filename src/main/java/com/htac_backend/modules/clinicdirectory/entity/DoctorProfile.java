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
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
    @Column(name = "professional_title", length = 100)
    private String professionalTitle;
    @Column(length = 150)
    private String position;
    @Column(name = "practice_start_year")
    private Short practiceStartYear;
    @Column(columnDefinition = "text")
    private String introduction;
    @Column(name = "clinical_expertise", columnDefinition = "text")
    private String clinicalExpertise;
    @Column(columnDefinition = "text")
    private String certifications;
    @Column(name = "is_public", nullable = false)
    private boolean publicProfile = false;

    protected DoctorProfile() { }

    public DoctorProfile(Long userId, Long specialtyId) {
        this.userId = userId;
        this.specialtyId = specialtyId;
    }

    public Long getUserId() { return userId; }
    public Long getSpecialtyId() { return specialtyId; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getProfessionalTitle() { return professionalTitle; }
    public String getPosition() { return position; }
    public Short getPracticeStartYear() { return practiceStartYear; }
    public String getIntroduction() { return introduction; }
    public String getClinicalExpertise() { return clinicalExpertise; }
    public String getCertifications() { return certifications; }
    public boolean isPublicProfile() { return publicProfile; }
    public void setPublicProfile(boolean publicProfile) { this.publicProfile = publicProfile; }

    public void update(Long specialtyId, String avatarUrl, String professionalTitle, String position,
            Short practiceStartYear, String introduction, String clinicalExpertise, String certifications) {
        this.specialtyId = specialtyId;
        this.avatarUrl = avatarUrl;
        this.professionalTitle = professionalTitle;
        this.position = position;
        this.practiceStartYear = practiceStartYear;
        this.introduction = introduction;
        this.clinicalExpertise = clinicalExpertise;
        this.certifications = certifications;
    }
}
