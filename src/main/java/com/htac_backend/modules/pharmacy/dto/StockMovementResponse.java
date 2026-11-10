package com.htac_backend.modules.pharmacy.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StockMovementResponse(Long documentId, String documentCode, Long itemId,
        String type, Instant occurredAt, String stockLocation, String stockBucket,
        BigDecimal deltaQuantity) { }
