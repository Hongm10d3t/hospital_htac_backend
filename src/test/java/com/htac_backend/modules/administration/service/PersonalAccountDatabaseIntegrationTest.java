package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.dto.AdminPasswordResetRequest;
import com.htac_backend.modules.administration.dto.ChangePasswordRequest;
import com.htac_backend.modules.administration.dto.UpdateOwnAccountRequest;
import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.PasswordResetVerificationMethod;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class PersonalAccountDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired PersonalAccountService personal;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void updatingDisplayNameDoesNotChangePatientIdentity() {
        UserAccount patientAccount = account(AccountRole.PATIENT);
        String patientCode = "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        jdbc.update("INSERT INTO patient (patient_code, user_id, full_name, date_of_birth, gender) "
                + "VALUES (?, ?, ?, ?, ?)", patientCode, patientAccount.getId(), "Identity Name",
                LocalDate.of(1990, 1, 2), "OTHER");
        authenticate(patientAccount);
        try {
            var response = personal.updateOwn(new UpdateOwnAccountRequest("Display Name", "0000000001"));
            assertEquals("Display Name", response.fullName());
            assertEquals(patientAccount.getId(), accounts.findById(patientAccount.getId()).orElseThrow().getUpdatedBy());
            assertEquals("Identity Name", jdbc.queryForObject(
                    "SELECT full_name FROM patient WHERE patient_code = ?", String.class, patientCode));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @Transactional
    void passwordChangeRequiresOldPasswordAndIncrementsSessionVersion() {
        String oldPassword = UUID.randomUUID().toString();
        UserAccount account = accounts.saveAndFlush(new UserAccount("self_" + UUID.randomUUID().toString().substring(0, 12),
                encoder.encode(oldPassword), "Self", "0000000000", AccountRole.PHARMACIST));
        authenticate(account);
        try {
            assertThrows(InvalidRequestException.class, () -> personal.changePassword(
                    new ChangePasswordRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString())));
            assertEquals(0L, account.getCredentialVersion());
            assertThrows(InvalidRequestException.class, () -> personal.changePassword(
                    new ChangePasswordRequest(oldPassword, oldPassword)));
            assertThrows(InvalidRequestException.class, () -> personal.changePassword(
                    new ChangePasswordRequest(oldPassword, UUID.randomUUID().toString().substring(0, 3))));
            String nextPassword = UUID.randomUUID().toString();
            personal.changePassword(new ChangePasswordRequest(oldPassword, nextPassword));
            assertEquals(1L, account.getCredentialVersion());
            assertTrue(encoder.matches(nextPassword, account.getPasswordHash()));
            assertFalse(encoder.matches(oldPassword, account.getPasswordHash()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @Transactional
    void adminResetRecordsActorAndMethodWithoutPlainPassword() {
        UserAccount admin = account(AccountRole.ADMIN);
        UserAccount target = account(AccountRole.RECEPTIONIST);
        String previousHash = target.getPasswordHash();
        authenticate(admin);
        try {
            String nextPassword = UUID.randomUUID().toString();
            personal.resetPassword(target.getId(), new AdminPasswordResetRequest(nextPassword,
                    PasswordResetVerificationMethod.IN_PERSON));
            assertNotEquals(previousHash, target.getPasswordHash());
            assertTrue(encoder.matches(nextPassword, target.getPasswordHash()));
            assertEquals(1L, target.getCredentialVersion());
            assertEquals(admin.getId(), target.getUpdatedBy());
            assertEquals(admin.getId(), jdbc.queryForObject(
                    "SELECT admin_user_id FROM account_password_reset_audit WHERE target_user_id = ?",
                    Long.class, target.getId()));
            assertEquals("IN_PERSON", jdbc.queryForObject(
                    "SELECT verified_via FROM account_password_reset_audit WHERE target_user_id = ?",
                    String.class, target.getId()));
            BusinessException blocked = assertThrows(BusinessException.class, () -> personal.resetPassword(
                    admin.getId(), new AdminPasswordResetRequest(UUID.randomUUID().toString(),
                            PasswordResetVerificationMethod.PHONE_CALLBACK)));
            assertEquals("ADMIN_RESET_FORBIDDEN", blocked.getCode());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UserAccount account(AccountRole role) {
        return accounts.saveAndFlush(new UserAccount("audit_" + UUID.randomUUID().toString().substring(0, 12),
                encoder.encode(UUID.randomUUID().toString()), "Test User", "0000000000", role));
    }

    private void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }
}
