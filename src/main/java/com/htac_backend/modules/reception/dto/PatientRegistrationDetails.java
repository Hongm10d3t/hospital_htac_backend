package com.htac_backend.modules.reception.dto;

import com.htac_backend.modules.reception.entity.PatientGender;
import java.time.LocalDate;

public record PatientRegistrationDetails(String citizenId, String fullName, LocalDate dateOfBirth,
                                         PatientGender gender, String address, String verificationToken) {
    @Override
    public String toString() { return "PatientRegistrationDetails[redacted]"; }
}
