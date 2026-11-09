package com.htac_backend.modules.administration.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
    @Override
    public String toString() { return "ChangePasswordRequest[redacted]"; }
}
