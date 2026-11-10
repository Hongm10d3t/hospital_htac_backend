package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.reception.dto.ClinicalPatientIdentity;
import com.htac_backend.modules.reception.repository.PatientRepository;
import com.htac_backend.shared.api.NotFoundException;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M04-owned minimal patient identity for M05 after assignment checks. */
@Service
public class ClinicalPatientIdentityService {
    private final PatientRepository patients;

    public ClinicalPatientIdentityService(PatientRepository patients) {
        this.patients = patients;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public ClinicalPatientIdentity get(Long patientId) {
        return patients.findById(patientId).map(ClinicalPatientIdentity::from)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Map<Long, ClinicalPatientIdentity> forPatients(Collection<Long> patientIds) {
        return patients.findAllById(patientIds).stream().map(ClinicalPatientIdentity::from)
                .collect(Collectors.toMap(ClinicalPatientIdentity::id, Function.identity()));
    }
}
