package com.htac_backend.modules.clinicdirectory.dto;

import com.htac_backend.modules.clinicdirectory.entity.DoctorProfile;

public record DoctorProfileAdminResponse(Long userId, Long specialtyId, String avatarUrl,
        String professionalTitle, String position, Short practiceStartYear, String introduction,
        String clinicalExpertise, String certifications, boolean publicProfile) {
    public static DoctorProfileAdminResponse from(DoctorProfile profile) {
        return new DoctorProfileAdminResponse(profile.getUserId(), profile.getSpecialtyId(),
                profile.getAvatarUrl(), profile.getProfessionalTitle(), profile.getPosition(),
                profile.getPracticeStartYear(), profile.getIntroduction(), profile.getClinicalExpertise(),
                profile.getCertifications(), profile.isPublicProfile());
    }
}
