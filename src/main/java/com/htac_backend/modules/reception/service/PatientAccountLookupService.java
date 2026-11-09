package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.reception.entity.Patient;
import com.htac_backend.modules.reception.repository.PatientRepository;
import com.htac_backend.shared.api.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M04-owned read contract for booking on the signed-in patient's own profile. */
@Service
public class PatientAccountLookupService {
    private final PatientRepository patients;

    public PatientAccountLookupService(PatientRepository patients) {
        this.patients = patients;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Long requirePatientId(Long accountId) {
        return patients.findByUserId(accountId).map(Patient::getId)
                .orElseThrow(() -> new BusinessException("PATIENT_PROFILE_REQUIRED",
                        "Tài khoản chưa liên kết hồ sơ bệnh nhân"));
    }
}
