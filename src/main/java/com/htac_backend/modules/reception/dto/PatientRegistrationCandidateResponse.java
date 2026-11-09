package com.htac_backend.modules.reception.dto;

import com.htac_backend.modules.reception.entity.Patient;
import java.time.LocalDate;

public record PatientRegistrationCandidateResponse(Long id, String patientCode, String fullName,
                                                   LocalDate dateOfBirth, boolean linked) {
    public static PatientRegistrationCandidateResponse from(Patient patient) {
        return new PatientRegistrationCandidateResponse(patient.getId(), patient.getPatientCode(),
                patient.getFullName(), patient.getDateOfBirth(), patient.isLinked());
    }
}
