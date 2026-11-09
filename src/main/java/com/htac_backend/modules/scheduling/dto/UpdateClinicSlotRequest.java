package com.htac_backend.modules.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

public record UpdateClinicSlotRequest(@NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt, @Positive int capacity) { }
