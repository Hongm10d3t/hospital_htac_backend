package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.dto.PatientRegistrationResponse;
import com.htac_backend.modules.administration.dto.RegisterPatientRequest;
import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.reception.dto.PatientRegistrationDetails;
import com.htac_backend.modules.reception.service.PatientRegistrationLinkService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientAccountRegistrationService {
    private final UserAccountRepository accounts;
    private final PatientRegistrationLinkService patientLinks;
    private final PasswordEncoder encoder;

    public PatientAccountRegistrationService(UserAccountRepository accounts,
            PatientRegistrationLinkService patientLinks, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.patientLinks = patientLinks;
        this.encoder = encoder;
    }

    @Transactional
    public PatientRegistrationResponse register(RegisterPatientRequest request) {
        if (request.password() == null || request.password().isBlank()
                || request.password().length() < 6 || request.password().length() > 128) {
            throw new InvalidRequestException("Mật khẩu phải có từ 6 đến 128 ký tự");
        }
        if (accounts.existsByUsername(request.username())) {
            throw new BusinessException("USERNAME_TAKEN", "Tên đăng nhập đã được sử dụng");
        }
        UserAccount account = accounts.saveAndFlush(new UserAccount(request.username(),
                encoder.encode(request.password()), request.fullName().trim(), request.phone().trim(),
                AccountRole.PATIENT));
        Long patientId = patientLinks.createOrLink(account.getId(), new PatientRegistrationDetails(
                request.citizenId(), request.fullName().trim(), request.dateOfBirth(), request.gender(),
                request.address() == null || request.address().isBlank() ? null : request.address().trim(),
                request.verificationToken()));
        return new PatientRegistrationResponse(account.getId(), patientId, "ACTIVE");
    }
}
