package com.htac_backend.modules.reporting.dto;

import java.math.BigDecimal;

public record StockMovementReportRow(Long medicineId, Long batchId, String stockLocation,
        String stockBucket, String type, BigDecimal inQuantity, BigDecimal outQuantity,
        BigDecimal netQuantity, long documentCount) { }
