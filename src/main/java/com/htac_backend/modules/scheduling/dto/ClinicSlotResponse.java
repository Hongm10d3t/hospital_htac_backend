package com.htac_backend.modules.scheduling.dto;

import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import com.htac_backend.modules.scheduling.entity.ClinicSlotStatus;
import java.time.Instant;

public record ClinicSlotResponse(Long id, Long roomId, Long doctorId, Instant startAt, Instant endAt,
        int capacity, ClinicSlotStatus status) {
    public static ClinicSlotResponse from(ClinicSlot slot) {
        return new ClinicSlotResponse(slot.getId(), slot.getRoomId(), slot.getDoctorId(),
                slot.getStartAt(), slot.getEndAt(), slot.getCapacity(), slot.getStatus());
    }
}
