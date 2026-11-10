package com.htac_backend.modules.outpatient.dto;

import java.time.Instant;

public record PatientPrescriptionSummary(Long id, String prescriptionCode, Long visitId,
        String status, Instant issuedAt, String note) { }
