package com.htac_backend.modules.reception.dto;

import java.time.Instant;

public record RegistrationVerificationResponse(String token, Instant expiresAt) {
    @Override
    public String toString() { return "RegistrationVerificationResponse[redacted]"; }
}
