package com.htac_backend.modules.pharmacy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpiryAlertResponse(Long batchId, String batchNumber, LocalDate expiryDate,
        long daysUntilExpiry, boolean expired, BigDecimal physicalQuantity) { }
