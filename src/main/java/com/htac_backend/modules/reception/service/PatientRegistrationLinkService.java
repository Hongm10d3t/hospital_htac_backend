package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.reception.dto.IssueRegistrationVerificationRequest;
import com.htac_backend.modules.reception.dto.PatientRegistrationDetails;
import com.htac_backend.modules.reception.dto.PatientRegistrationCandidateResponse;
import com.htac_backend.modules.reception.dto.RegistrationVerificationResponse;
import com.htac_backend.modules.reception.entity.Patient;
import com.htac_backend.modules.reception.repository.PatientRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientRegistrationLinkService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PatientRepository patients;

    public PatientRegistrationLinkService(PatientRepository patients) {
        this.patients = patients;
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(readOnly = true)
    public PageResponse<PatientRegistrationCandidateResponse> candidates(String citizenId, String fullName,
            LocalDate dateOfBirth, PageQuery page) {
        boolean byCitizenId = citizenId != null && !citizenId.isBlank();
        boolean byIdentity = fullName != null && !fullName.isBlank() && dateOfBirth != null;
        if (byCitizenId == byIdentity || (byCitizenId && (fullName != null || dateOfBirth != null))
                || (!byCitizenId && citizenId != null)) {
            throw new InvalidRequestException("Cần CCCD hoặc họ tên và ngày sinh");
        }
        if (byCitizenId) {
            if (!citizenId.matches("[0-9]{12}")) {
                throw new InvalidRequestException("CCCD không hợp lệ");
            }
            return PageResponse.from(patients.findByCitizenId(citizenId, page.toPageRequest())
                    .map(PatientRegistrationCandidateResponse::from));
        }
        return PageResponse.from(patients.findByFullNameIgnoreCaseAndDateOfBirth(
                fullName.trim(), dateOfBirth, page.toPageRequest())
                .map(PatientRegistrationCandidateResponse::from));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createOrLink(Long accountId, PatientRegistrationDetails details) {
        Patient existing = patients.findByCitizenIdForUpdate(details.citizenId()).orElse(null);
        if (existing != null) {
            if (existing.isLinked() || !sameIdentity(existing, details.fullName(), details.dateOfBirth())
                    || details.verificationToken() == null
                    || !existing.matchesRegistrationToken(sha256(details.verificationToken()), Instant.now())) {
                throw reviewRequired();
            }
            existing.link(accountId);
            patients.flush();
            return existing.getId();
        }
        if (details.verificationToken() != null
                || patients.existsByFullNameIgnoreCaseAndDateOfBirth(details.fullName(), details.dateOfBirth())) {
            throw reviewRequired();
        }
        String code = "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        Patient created = patients.saveAndFlush(new Patient(code, accountId, details.citizenId(),
                details.fullName(), details.dateOfBirth(), details.gender(), details.address()));
        return created.getId();
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional
    public RegistrationVerificationResponse issueVerification(Long patientId,
            IssueRegistrationVerificationRequest request) {
        Patient patient = patients.findByIdForUpdate(patientId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
        if (patient.isLinked()) {
            throw new BusinessException("PATIENT_ALREADY_LINKED", "Hồ sơ đã liên kết tài khoản");
        }
        if (!sameIdentity(patient, request.fullName().trim(), request.dateOfBirth())) {
            throw new BusinessException("IDENTITY_MISMATCH", "Thông tin cần được đối chiếu lại");
        }
        if (patient.getCitizenId() != null && !patient.getCitizenId().equals(request.citizenId())) {
            throw new BusinessException("IDENTITY_MISMATCH", "Thông tin cần được đối chiếu lại");
        }
        if (patient.getCitizenId() == null && patients.existsByCitizenId(request.citizenId())) {
            throw new BusinessException("IDENTITY_MISMATCH", "Thông tin cần được đối chiếu lại");
        }
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        patient.setCitizenId(request.citizenId());
        patient.setRegistrationToken(sha256(token), expiresAt);
        patients.flush();
        return new RegistrationVerificationResponse(token, expiresAt);
    }

    private boolean sameIdentity(Patient patient, String fullName, java.time.LocalDate dateOfBirth) {
        return patient.getFullName().equalsIgnoreCase(fullName) && patient.getDateOfBirth().equals(dateOfBirth);
    }

    private BusinessException reviewRequired() {
        return new BusinessException("IDENTITY_REVIEW_REQUIRED", "Cần nhân viên tiếp đón đối chiếu hồ sơ");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
