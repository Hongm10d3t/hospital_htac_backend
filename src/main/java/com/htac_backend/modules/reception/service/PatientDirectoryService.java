package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.reception.dto.CreatePatientRequest;
import com.htac_backend.modules.reception.dto.PatientResponse;
import com.htac_backend.modules.reception.dto.UpdatePatientRequest;
import com.htac_backend.modules.reception.entity.Patient;
import com.htac_backend.modules.reception.repository.PatientRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientDirectoryService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final PatientRepository patients;
    private final CurrentPatientActor actor;
    private final AccountRoleLookupService accounts;

    public PatientDirectoryService(PatientRepository patients, CurrentPatientActor actor,
            AccountRoleLookupService accounts) {
        this.patients = patients;
        this.actor = actor;
        this.accounts = accounts;
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> search(String patientCode, String citizenId, String fullName,
            LocalDate dateOfBirth, PageQuery page) {
        boolean byCode = patientCode != null && !patientCode.isBlank();
        boolean byCitizen = citizenId != null && !citizenId.isBlank();
        boolean byIdentity = fullName != null && !fullName.isBlank() && dateOfBirth != null;
        if ((byCode ? 1 : 0) + (byCitizen ? 1 : 0) + (byIdentity ? 1 : 0) != 1
                || (byCode && (citizenId != null || fullName != null || dateOfBirth != null))
                || (byCitizen && (patientCode != null || fullName != null || dateOfBirth != null))
                || (byIdentity && (patientCode != null || citizenId != null))) {
            throw new InvalidRequestException("Cần mã bệnh nhân, CCCD hoặc họ tên cùng ngày sinh");
        }
        if (byCode) {
            return PageResponse.from(patients.findByPatientCode(patientCode.trim(), page.toPageRequest())
                    .map(PatientResponse::from));
        }
        if (byCitizen) {
            if (!citizenId.matches("[0-9]{12}")) {
                throw new InvalidRequestException("CCCD không hợp lệ");
            }
            return PageResponse.from(patients.findByCitizenId(citizenId, page.toPageRequest())
                    .map(PatientResponse::from));
        }
        return PageResponse.from(patients.findByFullNameIgnoreCaseAndDateOfBirth(
                fullName.trim(), dateOfBirth, page.toPageRequest()).map(PatientResponse::from));
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(readOnly = true)
    public PatientResponse get(Long id) {
        return PatientResponse.from(patients.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân")));
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional(readOnly = true)
    public PatientResponse own() {
        Long accountId = actor.userId();
        if (!accounts.hasActiveRole(accountId, AccountRole.PATIENT)) {
            throw new BusinessException("PATIENT_ACCOUNT_INACTIVE", "Tài khoản bệnh nhân không hoạt động");
        }
        return PatientResponse.from(patients.findByUserId(accountId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân")));
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional
    public PatientResponse create(CreatePatientRequest body) {
        validate(body.citizenId(), body.fullName(), body.dateOfBirth());
        if (body.citizenId() != null && patients.existsByCitizenId(body.citizenId())) {
            throw citizenConflict();
        }
        String code = "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        Patient created = patients.saveAndFlush(new Patient(code, null, body.citizenId(),
                body.fullName().trim(), body.dateOfBirth(), body.gender(), blankToNull(body.address())));
        return PatientResponse.from(created);
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional
    public PatientResponse update(Long id, UpdatePatientRequest body) {
        Patient patient = patients.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
        validate(body.citizenId(), body.fullName(), body.dateOfBirth());
        if (body.citizenId() != null && patients.existsByCitizenIdAndIdNot(body.citizenId(), id)) {
            throw citizenConflict();
        }
        patient.updateIdentity(body.citizenId(), body.fullName().trim(), body.dateOfBirth(),
                body.gender(), blankToNull(body.address()));
        patients.flush();
        return PatientResponse.from(patient);
    }

    private void validate(String citizenId, String fullName, LocalDate dateOfBirth) {
        if (fullName == null || fullName.isBlank() || fullName.length() > 150 || dateOfBirth == null
                || dateOfBirth.isAfter(LocalDate.now(HOSPITAL_ZONE))
                || (citizenId != null && !citizenId.matches("[0-9]{12}"))) {
            throw new InvalidRequestException("Thông tin định danh không hợp lệ");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException citizenConflict() {
        return new BusinessException("PATIENT_CITIZEN_ID_TAKEN", "CCCD đã thuộc hồ sơ khác");
    }
}
