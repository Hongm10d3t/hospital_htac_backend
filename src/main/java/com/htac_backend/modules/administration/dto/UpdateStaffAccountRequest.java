package com.htac_backend.modules.administration.dto;

import com.htac_backend.modules.administration.entity.AccountRole;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStaffAccountRequest(
        @Pattern(regexp = "[A-Za-z0-9._-]{3,50}") String username,
        @Size(min = 1, max = 150) String fullName,
        @Size(min = 1, max = 20) String phone,
        AccountRole role,
        Long specialtyId) {
}
