package com.htac_backend.modules.outpatient.dto;

import java.util.List;

public record PatientPrescriptionDetail(PatientPrescriptionSummary prescription,
        List<PatientPrescriptionItem> items) { }
