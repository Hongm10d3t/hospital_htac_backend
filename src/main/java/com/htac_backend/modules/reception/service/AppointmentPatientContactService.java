package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.administration.service.AccountContactLookupService;
import com.htac_backend.modules.reception.dto.AppointmentPatientContact;
import com.htac_backend.modules.reception.entity.Patient;
import com.htac_backend.modules.reception.repository.PatientRepository;
import com.htac_backend.shared.api.NotFoundException;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentPatientContactService {
    private final PatientRepository patients;
    private final AccountContactLookupService accounts;

    public AppointmentPatientContactService(PatientRepository patients, AccountContactLookupService accounts) {
        this.patients = patients;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public AppointmentPatientContact forPatient(Long patientId) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
        if (patient.getUserId() == null) {
            throw new NotFoundException("Bệnh nhân chưa có tài khoản liên hệ");
        }
        return new AppointmentPatientContact(patient.getFullName(), accounts.phoneFor(patient.getUserId()));
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Map<Long, AppointmentPatientContact> forPatients(Collection<Long> patientIds) {
        Map<Long, Patient> found = patients.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
        Map<Long, String> phones = accounts.phonesFor(found.values().stream()
                .map(Patient::getUserId).toList());
        return found.values().stream().collect(Collectors.toMap(Patient::getId,
                patient -> new AppointmentPatientContact(patient.getFullName(),
                        phones.get(patient.getUserId()))));
    }
}
