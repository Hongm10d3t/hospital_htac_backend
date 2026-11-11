package com.htac_backend.modules.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DispenseReportLine(Long documentId, String documentCode, Long itemId,
        Long prescriptionItemId, Long batchId, Instant occurredAt, BigDecimal quantity) { }
