package com.htac_backend.modules.outpatient.dto;

import java.time.LocalDate;
import java.util.List;

public record PatientVisitDetail(PatientVisitSummary visit, String diagnosisCode, String clinicalNotes,
        String treatmentPlan, LocalDate followUpDate, List<PatientPrescriptionSummary> prescriptions) { }
