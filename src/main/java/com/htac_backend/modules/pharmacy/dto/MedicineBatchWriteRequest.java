package com.htac_backend.modules.pharmacy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record MedicineBatchWriteRequest(
        @NotNull Long medicineId,
        @NotBlank String batchNumber,
        @NotNull LocalDate expiryDate,
        @NotBlank String status,
        String blockReason) { }
