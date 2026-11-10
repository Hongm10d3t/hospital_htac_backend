package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.reception.repository.PatientRepository;
import com.htac_backend.shared.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceptionPatientLookupService {
    private final PatientRepository patients;

    public ReceptionPatientLookupService(PatientRepository patients) {
        this.patients = patients;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void requirePatient(Long patientId) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("Không tìm thấy hồ sơ bệnh nhân");
        }
    }
}
