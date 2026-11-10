package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.reception.dto.CreatePatientRequest;
import com.htac_backend.modules.reception.dto.UpdatePatientRequest;
import com.htac_backend.modules.reception.entity.Patient;
import com.htac_backend.modules.reception.entity.PatientGender;
import com.htac_backend.modules.reception.repository.PatientRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.PageQuery;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class PatientDirectoryDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired PatientDirectoryService directory;
    @Autowired PatientRepository patients;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder encoder;

    @Test
    @Transactional
    @WithMockUser(roles = "RECEPTIONIST")
    void directPatientNeedsNoAccountOrCitizenIdAndUpdatesKeepIdentityLinks() {
        var direct = directory.create(new CreatePatientRequest(null, "Direct Patient",
                LocalDate.of(1991, 2, 3), PatientGender.OTHER, null));
        assertNull(direct.userId());
        assertNull(direct.citizenId());
        assertEquals(1, directory.search(direct.patientCode(), null, null, null, page()).totalItems());
        assertEquals(1, directory.search(null, null, "Direct Patient", LocalDate.of(1991, 2, 3),
                page()).totalItems());

        String citizen = "123456789012";
        var updated = directory.update(direct.id(), new UpdatePatientRequest(citizen, "Direct Patient",
                LocalDate.of(1991, 2, 3), PatientGender.OTHER, "New address"));
        assertEquals(direct.patientCode(), updated.patientCode());
        assertEquals(1, directory.search(null, citizen, null, null, page()).totalItems());
        assertEquals("PATIENT_CITIZEN_ID_TAKEN", assertThrows(BusinessException.class,
                () -> directory.create(new CreatePatientRequest(citizen, "Different Patient",
                        LocalDate.of(1990, 1, 1), PatientGender.MALE, null))).getCode());

        UserAccount account = account();
        Patient linked = patients.saveAndFlush(new Patient(code("P"), account.getId(), null,
                "Linked Patient", LocalDate.of(1990, 1, 1), PatientGender.FEMALE, null));
        String tokenHash = "00".repeat(32);
        linked.setRegistrationToken(tokenHash, Instant.now().plusSeconds(600));
        patients.flush();
        assertTrue(linked.matchesRegistrationToken(tokenHash, Instant.now()));
        var changed = directory.update(linked.getId(), new UpdatePatientRequest(null, "Corrected Name",
                LocalDate.of(1990, 1, 1), PatientGender.FEMALE, null));
        assertEquals(account.getId(), changed.userId());
        assertEquals(linked.getPatientCode(), changed.patientCode());
        assertFalse(linked.matchesRegistrationToken(tokenHash, Instant.now()));
    }

    @Test
    @Transactional
    @WithMockUser(roles = "PATIENT")
    void patientCanReadOnlyLinkedProfileFromPrincipal() {
        UserAccount first = account();
        UserAccount second = account();
        Patient own = patients.saveAndFlush(new Patient(code("P"), first.getId(), null, "First",
                LocalDate.of(1990, 1, 1), PatientGender.OTHER, null));
        patients.saveAndFlush(new Patient(code("P"), second.getId(), null, "Second",
                LocalDate.of(1990, 1, 1), PatientGender.OTHER, null));
        AccountPrincipal principal = new AccountPrincipal(first);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        assertEquals(own.getId(), directory.own().id());
        assertEquals("First", directory.own().fullName());
    }

    @Test
    @Transactional
    @WithMockUser(roles = "RECEPTIONIST")
    void databaseRejectsDuplicatePatientCode() {
        var first = directory.create(new CreatePatientRequest(null, "First Patient",
                LocalDate.of(1991, 2, 3), PatientGender.OTHER, null));
        assertThrows(DataIntegrityViolationException.class, () -> patients.saveAndFlush(new Patient(
                first.patientCode(), null, null, "Second Patient", LocalDate.of(1992, 2, 3),
                PatientGender.OTHER, null)));
    }

    private UserAccount account() {
        return accounts.saveAndFlush(new UserAccount(code("u"), encoder.encode(UUID.randomUUID().toString()),
                "Test Account", "0000000000", AccountRole.PATIENT));
    }

    private PageQuery page() { return PageQuery.parse(0, 20, null, "id", Set.of("id")); }
    private String code(String prefix) { return prefix + UUID.randomUUID().toString().substring(0, 10); }
}
