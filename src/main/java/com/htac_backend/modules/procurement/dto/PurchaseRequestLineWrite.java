package com.htac_backend.modules.procurement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PurchaseRequestLineWrite(@NotNull @Positive Long medicineId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
        String note) { }
