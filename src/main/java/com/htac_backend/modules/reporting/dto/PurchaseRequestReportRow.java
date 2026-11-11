package com.htac_backend.modules.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PurchaseRequestReportRow(Long requestId, String requestCode, String status,
        Long supplierId, Long medicineId, Instant reviewedAt, Instant orderedAt,
        LocalDate expectedDeliveryDate, Instant closedAt, BigDecimal requestedQuantity,
        BigDecimal approvedQuantity, BigDecimal receivedQuantity, BigDecimal remainingQuantity) { }
