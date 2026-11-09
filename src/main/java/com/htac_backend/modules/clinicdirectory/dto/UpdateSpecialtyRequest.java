package com.htac_backend.modules.clinicdirectory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSpecialtyRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,20}") String code,
        @NotBlank @Size(max = 100) String name) {
}
