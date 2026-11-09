package com.htac_backend.modules.reception.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record IssueRegistrationVerificationRequest(
        @NotBlank @Pattern(regexp = "[0-9]{12}") String citizenId,
        @NotBlank @Size(max = 150) String fullName,
        @NotNull LocalDate dateOfBirth) {
    @Override
    public String toString() { return "IssueRegistrationVerificationRequest[redacted]"; }
}
