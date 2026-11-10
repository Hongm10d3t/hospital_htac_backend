package com.htac_backend.modules.pharmacy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record OpeningStockRequest(@NotNull LocalDate businessDate,
        @NotBlank String stockLocation, @NotBlank String stockBucket,
        String note, @NotEmpty List<@Valid InboundStockLineRequest> items) { }
