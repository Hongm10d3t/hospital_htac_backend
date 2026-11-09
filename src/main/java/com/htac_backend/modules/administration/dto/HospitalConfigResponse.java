package com.htac_backend.modules.administration.dto;

import com.htac_backend.modules.administration.entity.HospitalConfig;

public record HospitalConfigResponse(String name, String address, String phone, String email,
                                     String logoUrl, String description, int advanceBookingDays) {
    public static HospitalConfigResponse from(HospitalConfig config) {
        return new HospitalConfigResponse(config.getName(), config.getAddress(), config.getPhone(),
                config.getEmail(), config.getLogoUrl(), config.getDescription(),
                config.getAdvanceBookingDays());
    }
}
