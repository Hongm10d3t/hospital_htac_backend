package com.htac_backend.modules.reporting.dto;

import java.math.BigDecimal;

public record PrescriptionReportItem(Long id, Long medicineId, BigDecimal prescribedQuantity,
        BigDecimal dispensedQuantity, BigDecimal currentUnfilledQuantity,
        String unfilledReason, String unfilledNote) { }
