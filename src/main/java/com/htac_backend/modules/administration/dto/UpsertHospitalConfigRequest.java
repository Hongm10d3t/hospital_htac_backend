package com.htac_backend.modules.administration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertHospitalConfigRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 2000) String address,
        @NotBlank @Size(max = 20) String phone,
        @Email @Size(max = 150) String email,
        @Size(max = 500) String logoUrl,
        @Size(max = 10000) String description,
        @NotNull @Min(1) Integer advanceBookingDays) {
}
