package com.htac_backend.modules.administration.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
