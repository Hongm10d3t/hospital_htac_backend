package com.htac_backend.modules.reporting.dto;

import java.math.BigDecimal;

public record PrescriptionMedicineReportRow(Long medicineId, String medicineCode,
        String medicineName, String baseUnit, BigDecimal prescribedQuantity,
        BigDecimal dispensedQuantity, BigDecimal currentUnfilledQuantity,
        long uncheckedLineCount) { }
