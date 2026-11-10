package com.htac_backend.modules.pharmacy.dto;

import java.math.BigDecimal;
import java.util.List;

public record MedicineInventoryResponse(Long medicineId, String code, String name,
        String baseUnit, boolean active, BigDecimal minimumStock,
        BigDecimal physicalQuantity, BigDecimal dispensableQuantity,
        boolean belowMinimum, int expiryWarningDays,
        List<ExpiryAlertResponse> expiryAlerts,
        List<InventoryPositionResponse> positions) { }
