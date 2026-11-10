package com.htac_backend.modules.pharmacy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record AdjustmentRequest(@NotNull OffsetDateTime occurredAt,
        @NotBlank String stockLocation, @NotBlank String stockBucket,
        @NotBlank String reason,
        @NotEmpty List<@Valid AdjustmentLineRequest> items) { }
