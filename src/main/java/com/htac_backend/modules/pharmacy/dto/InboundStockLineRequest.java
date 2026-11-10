package com.htac_backend.modules.pharmacy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record InboundStockLineRequest(@NotNull Long batchId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3)
        BigDecimal quantity,
        @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitCost,
        String note) { }
