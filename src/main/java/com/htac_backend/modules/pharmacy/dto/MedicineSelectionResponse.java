package com.htac_backend.modules.pharmacy.dto;

import com.htac_backend.modules.pharmacy.entity.Medicine;

public record MedicineSelectionResponse(Long id, String code, String name, String activeIngredient,
        String strength, String dosageForm, String baseUnit) {
    public static MedicineSelectionResponse from(Medicine medicine) {
        return new MedicineSelectionResponse(medicine.getId(), medicine.getCode(), medicine.getName(),
                medicine.getActiveIngredient(), medicine.getStrength(), medicine.getDosageForm(),
                medicine.getBaseUnit());
    }
}
