package com.htac_backend.modules.pharmacy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MedicineWriteRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 200) String activeIngredient,
        @NotBlank @Size(max = 100) String strength,
        @NotBlank @Size(max = 50) String dosageForm,
        @NotBlank @Size(max = 20) String baseUnit,
        @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal minimumStock,
        @NotNull @Min(0) Integer leadTimeDays,
        @NotNull Boolean active) { }
