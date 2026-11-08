package com.htac_backend.modules.administration.dto;

public record CsrfResponse(String headerName, String token) {
    @Override
    public String toString() {
        return "CsrfResponse[redacted]";
    }
}
