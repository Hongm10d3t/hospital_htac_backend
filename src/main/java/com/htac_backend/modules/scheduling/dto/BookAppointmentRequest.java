package com.htac_backend.modules.scheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BookAppointmentRequest(@NotNull @Positive Long slotId,
        @NotBlank @Size(max = 3000) String reason) { }
