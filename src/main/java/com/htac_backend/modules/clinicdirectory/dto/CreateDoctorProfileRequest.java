package com.htac_backend.modules.clinicdirectory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateDoctorProfileRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long specialtyId,
        @Size(max = 500) String avatarUrl,
        @Size(max = 100) String professionalTitle,
        @Size(max = 150) String position,
        @Min(1900) @Max(9999) Short practiceStartYear,
        @Size(max = 3000) String introduction,
        @Size(max = 3000) String clinicalExpertise,
        @Size(max = 3000) String certifications) {
}
