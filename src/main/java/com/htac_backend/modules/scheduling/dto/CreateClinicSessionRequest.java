package com.htac_backend.modules.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CreateClinicSessionRequest(@NotNull @Positive Long roomId, @NotNull @Positive Long doctorId,
        @NotNull @Size(min = 1, max = 7) List<@NotNull LocalDate> dates,
        @NotNull LocalTime startTime, @NotNull LocalTime endTime,
        @Positive int durationMinutes, @Positive int capacity) { }
