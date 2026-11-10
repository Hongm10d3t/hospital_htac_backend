package com.htac_backend.modules.reception.dto;

import com.htac_backend.modules.reception.entity.Patient;
import com.htac_backend.modules.reception.entity.PatientGender;
import java.time.LocalDate;

public record PatientResponse(Long id, String patientCode, Long userId, String citizenId,
        String fullName, LocalDate dateOfBirth, PatientGender gender, String address) {
    public static PatientResponse from(Patient patient) {
        return new PatientResponse(patient.getId(), patient.getPatientCode(), patient.getUserId(),
                patient.getCitizenId(), patient.getFullName(), patient.getDateOfBirth(),
                patient.getGender(), patient.getAddress());
    }
}
