package com.htac_backend.modules.clinicdirectory.dto;

import com.htac_backend.modules.clinicdirectory.entity.DoctorProfile;

public record DoctorPublicResponse(Long userId, String fullName, Long specialtyId, String specialtyName,
        String avatarUrl, String professionalTitle, String position, Short practiceStartYear,
        String introduction, String clinicalExpertise, String certifications, String availableSlotsUrl) {
    public static DoctorPublicResponse from(DoctorProfile profile, String fullName, String specialtyName) {
        return new DoctorPublicResponse(profile.getUserId(), fullName, profile.getSpecialtyId(), specialtyName,
                profile.getAvatarUrl(), profile.getProfessionalTitle(), profile.getPosition(),
                profile.getPracticeStartYear(), profile.getIntroduction(), profile.getClinicalExpertise(),
                profile.getCertifications(), "/api/v1/public/clinic-slots?doctorId=" + profile.getUserId());
    }
}
