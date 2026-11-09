package com.htac_backend.modules.administration.dto;

import com.htac_backend.modules.reception.entity.PatientGender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RegisterPatientRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,50}") String username,
        String password,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Pattern(regexp = "[0-9]{12}") String citizenId,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @NotNull PatientGender gender,
        @Size(max = 2000) String address,
        @Size(max = 100) String verificationToken) {
    @Override
    public String toString() { return "RegisterPatientRequest[redacted]"; }
}
