package com.htac_backend.modules.clinicdirectory.dto;

import com.htac_backend.modules.clinicdirectory.entity.ClinicRoom;

public record ClinicRoomResponse(Long id, Long specialtyId, String code, String name,
                                 String location, boolean active) {
    public static ClinicRoomResponse from(ClinicRoom room) {
        return new ClinicRoomResponse(room.getId(), room.getSpecialtyId(), room.getCode(), room.getName(),
                room.getLocation(), room.isActive());
    }
}
