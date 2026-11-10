package com.htac_backend.modules.outpatient.dto;

import java.math.BigDecimal;

public record PatientPrescriptionItem(Long medicineId, String medicineName, String baseUnit,
        BigDecimal quantity, String usageInstruction) { }
