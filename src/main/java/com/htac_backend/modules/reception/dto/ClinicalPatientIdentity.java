package com.htac_backend.modules.reception.dto;

import com.htac_backend.modules.reception.entity.Patient;
import com.htac_backend.modules.reception.entity.PatientGender;
import java.time.LocalDate;

public record ClinicalPatientIdentity(Long id, String patientCode, String fullName,
        LocalDate dateOfBirth, PatientGender gender) {
    public static ClinicalPatientIdentity from(Patient patient) {
        return new ClinicalPatientIdentity(patient.getId(), patient.getPatientCode(),
                patient.getFullName(), patient.getDateOfBirth(), patient.getGender());
    }
}
