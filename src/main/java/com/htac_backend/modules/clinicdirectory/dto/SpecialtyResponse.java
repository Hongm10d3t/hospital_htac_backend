package com.htac_backend.modules.clinicdirectory.dto;

import com.htac_backend.modules.clinicdirectory.entity.Specialty;

public record SpecialtyResponse(Long id, String code, String name, boolean active) {
    public static SpecialtyResponse from(Specialty specialty) {
        return new SpecialtyResponse(specialty.getId(), specialty.getCode(), specialty.getName(),
                specialty.isActive());
    }
}
