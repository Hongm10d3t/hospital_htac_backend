package com.htac_backend.modules.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

public record CreateClinicSlotRequest(@NotNull @Positive Long roomId, @NotNull @Positive Long doctorId,
        @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt, @Positive int capacity) { }
