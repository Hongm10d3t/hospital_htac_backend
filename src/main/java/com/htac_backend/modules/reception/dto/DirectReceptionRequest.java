package com.htac_backend.modules.reception.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DirectReceptionRequest(@NotNull Long patientId, @NotNull Long slotId,
        @Size(max = 3000) String reason) { }
