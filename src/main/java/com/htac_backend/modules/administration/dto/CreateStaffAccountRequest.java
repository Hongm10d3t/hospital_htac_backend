package com.htac_backend.modules.administration.dto;

import com.htac_backend.modules.administration.entity.AccountRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStaffAccountRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,50}") String username,
        String password,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) String phone,
        @NotNull AccountRole role,
        Long specialtyId) {
    @Override
    public String toString() { return "CreateStaffAccountRequest[redacted]"; }
}
