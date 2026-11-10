package com.htac_backend.modules.outpatient.dto;

import com.htac_backend.modules.outpatient.entity.PrescriptionItem;
import com.htac_backend.modules.pharmacy.dto.MedicineLabel;
import java.math.BigDecimal;

public record PrescriptionLineResponse(Long id, Long medicineId, String medicineName, String baseUnit,
        BigDecimal quantity, String usageInstruction, BigDecimal unfilledQuantity,
        String unfilledReason, String unfilledNote) {
    public static PrescriptionLineResponse from(PrescriptionItem item, MedicineLabel medicine) {
        return new PrescriptionLineResponse(item.getId(), item.getMedicineId(), medicine.name(),
                medicine.baseUnit(), item.getQuantity(), item.getUsageInstruction(),
                item.getUnfilledQuantity(), item.getUnfilledReason(), item.getUnfilledNote());
    }
}
