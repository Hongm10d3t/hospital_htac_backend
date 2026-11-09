package com.htac_backend.modules.scheduling.dto;

import java.time.Instant;

public record PublicClinicSlotResponse(Long id, Long roomId, String roomName, Long doctorId,
        Long specialtyId, String specialtyName, Instant startAt, Instant endAt,
        int capacity, int available) { }
