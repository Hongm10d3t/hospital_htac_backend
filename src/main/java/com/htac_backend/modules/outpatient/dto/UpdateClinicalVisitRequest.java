package com.htac_backend.modules.outpatient.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateClinicalVisitRequest(
        @Size(max = 10000) String clinicalNotes,
        @Size(max = 20) String diagnosisCode,
        @Size(max = 5000) String diagnosis,
        @Size(max = 10000) String treatmentPlan,
        LocalDate followUpDate) { }
