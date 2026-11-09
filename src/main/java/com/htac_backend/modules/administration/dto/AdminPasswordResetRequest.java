package com.htac_backend.modules.administration.dto;

import com.htac_backend.modules.administration.entity.PasswordResetVerificationMethod;
import jakarta.validation.constraints.NotNull;

public record AdminPasswordResetRequest(String newPassword,
                                        @NotNull PasswordResetVerificationMethod verifiedVia) {
    @Override
    public String toString() { return "AdminPasswordResetRequest[redacted]"; }
}
