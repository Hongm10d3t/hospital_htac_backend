package com.htac_backend.modules.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StockMovementReportLine(Long documentId, String documentCode, Long itemId,
        String type, Instant occurredAt, Long medicineId, Long batchId,
        String stockLocation, String stockBucket, BigDecimal deltaQuantity) { }
