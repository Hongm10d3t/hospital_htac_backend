package com.htac_backend.modules.pharmacy.dto;

import java.math.BigDecimal;

public record DispensingLineResponse(Long prescriptionItemId, Long medicineId,
        String medicineName, String baseUnit, BigDecimal prescribedQuantity,
        BigDecimal dispensedQuantity, BigDecimal remainingQuantity,
        BigDecimal unfilledQuantity, String unfilledReason, String unfilledNote) { }
