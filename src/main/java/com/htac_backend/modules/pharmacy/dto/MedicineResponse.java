package com.htac_backend.modules.pharmacy.dto;

import com.htac_backend.modules.pharmacy.entity.Medicine;
import java.math.BigDecimal;

public record MedicineResponse(Long id, String code, String name, String activeIngredient,
        String strength, String dosageForm, String baseUnit, BigDecimal minimumStock,
        int leadTimeDays, boolean active) {
    public static MedicineResponse from(Medicine medicine) {
        return new MedicineResponse(medicine.getId(), medicine.getCode(), medicine.getName(),
                medicine.getActiveIngredient(), medicine.getStrength(), medicine.getDosageForm(),
                medicine.getBaseUnit(), medicine.getMinimumStock(), medicine.getLeadTimeDays(),
                medicine.isActive());
    }
}
