package com.htac_backend.modules.pharmacy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UnfilledLineRequest(@NotNull Long prescriptionItemId,
        @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal unfilledQuantity,
        String reason, String note) { }
