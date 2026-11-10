package com.htac_backend.modules.outpatient.dto;

import java.math.BigDecimal;

public record PrescriptionUnfilledUpdate(Long prescriptionItemId, BigDecimal quantity,
        String reason, String note) { }
