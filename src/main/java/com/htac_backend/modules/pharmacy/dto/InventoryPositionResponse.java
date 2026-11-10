package com.htac_backend.modules.pharmacy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryPositionResponse(Long batchId, String batchNumber,
        LocalDate expiryDate, String batchStatus, String stockLocation,
        String stockBucket, BigDecimal physicalQuantity, BigDecimal dispensableQuantity) { }
