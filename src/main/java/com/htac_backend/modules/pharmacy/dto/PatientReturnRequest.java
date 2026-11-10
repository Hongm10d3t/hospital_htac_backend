package com.htac_backend.modules.pharmacy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record PatientReturnRequest(@NotNull Long prescriptionId,
        @NotNull OffsetDateTime occurredAt, @NotBlank String reason,
        @NotEmpty List<@Valid PatientReturnLineRequest> items) { }
