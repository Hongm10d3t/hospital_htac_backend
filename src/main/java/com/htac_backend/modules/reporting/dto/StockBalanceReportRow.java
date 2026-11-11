package com.htac_backend.modules.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockBalanceReportRow(Long medicineId, String medicineCode, Long batchId,
        String batchNumber, LocalDate expiryDate, String batchStatus, String stockLocation,
        String stockBucket, BigDecimal physicalQuantity, BigDecimal dispensableQuantity) { }
