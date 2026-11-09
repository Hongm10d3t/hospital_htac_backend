package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.dto.PatientRegistrationResponse;
import com.htac_backend.modules.administration.dto.RegisterPatientRequest;
import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.reception.dto.IssueRegistrationVerificationRequest;
import com.htac_backend.modules.reception.entity.PatientGender;
import com.htac_backend.modules.reception.repository.PatientRepository;
import com.htac_backend.modules.reception.service.PatientRegistrationLinkService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.PageQuery;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class PatientRegistrationDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired PatientAccountRegistrationService registrations;
    @Autowired PatientRegistrationLinkService links;
    @Autowired UserAccountRepository accounts;
    @Autowired PatientRepository patients;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void newIdentityCreatesPatientAccountAndNoRawPassword() {
        String citizenId = citizenId();
        PatientRegistrationResponse result = registrations.register(request(username(), citizenId, null));
        var account = accounts.findById(result.accountId()).orElseThrow();
        var patient = patients.findById(result.patientId()).orElseThrow();
        assertEquals(AccountRole.PATIENT, account.getRole());
        assertEquals(account.getId(), patient.getUserId());
        assertFalse(account.getPasswordHash().contains("plain"));
        assertEquals("ACTIVE", result.status());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void existingUnlinkedRecordRequiresVerificationAndIsReused() {
        String citizenId = citizenId();
        long patientId = seedPatient(null, citizenId);
        String username = username();
        try {
            var candidates = links.candidates(citizenId, null, null,
                    PageQuery.parse(0, 20, null, "id", Set.of("id")));
            assertEquals(patientId, candidates.items().getFirst().id());
            BusinessException review = assertThrows(BusinessException.class, () ->
                    registrations.register(request(username, citizenId, null)));
            assertEquals("IDENTITY_REVIEW_REQUIRED", review.getCode());
            assertFalse(accounts.existsByUsername(username));

            var issued = links.issueVerification(patientId,
                    new IssueRegistrationVerificationRequest(citizenId, "Synthetic Patient", LocalDate.of(1990, 1, 2)));
            assertNotNull(issued.token());
            jdbc.update("UPDATE patient SET registration_token_expires_at = "
                    + "DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE id = ?", patientId);
            BusinessException expired = assertThrows(BusinessException.class, () ->
                    registrations.register(request(username, citizenId, issued.token())));
            assertEquals("IDENTITY_REVIEW_REQUIRED", expired.getCode());
            assertFalse(accounts.existsByUsername(username));

            var refreshed = links.issueVerification(patientId,
                    new IssueRegistrationVerificationRequest(citizenId, "Synthetic Patient", LocalDate.of(1990, 1, 2)));
            PatientRegistrationResponse linked = registrations.register(request(username, citizenId, refreshed.token()));
            assertEquals(patientId, linked.patientId());
            assertEquals(linked.accountId(), patients.findById(patientId).orElseThrow().getUserId());
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM patient WHERE citizen_id = ?",
                    Long.class, citizenId));
            BusinessException reused = assertThrows(BusinessException.class, () ->
                    registrations.register(request(username(), citizenId, refreshed.token())));
            assertEquals("IDENTITY_REVIEW_REQUIRED", reused.getCode());
        } finally {
            jdbc.update("DELETE FROM patient WHERE id = ?", patientId);
            jdbc.update("DELETE FROM user_account WHERE username = ?", username);
        }
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void oldRecordWithoutCitizenIdIsReviewedBeforeLinking() {
        long patientId = seedPatient(null, null);
        String citizenId = citizenId();
        String username = username();
        try {
            BusinessException review = assertThrows(BusinessException.class, () ->
                    registrations.register(request(username, citizenId, null)));
            assertEquals("IDENTITY_REVIEW_REQUIRED", review.getCode());
            var issued = links.issueVerification(patientId,
                    new IssueRegistrationVerificationRequest(citizenId, "Synthetic Patient", LocalDate.of(1990, 1, 2)));
            PatientRegistrationResponse linked = registrations.register(request(username, citizenId, issued.token()));
            assertEquals(patientId, linked.patientId());
            assertEquals(citizenId, patients.findById(patientId).orElseThrow().getCitizenId());
        } finally {
            jdbc.update("DELETE FROM patient WHERE id = ?", patientId);
            jdbc.update("DELETE FROM user_account WHERE username = ?", username);
        }
    }

    @Test
    void concurrentSameIdentityCreatesOnlyOneAccountAndPatient() throws Exception {
        String citizenId = citizenId();
        String first = username();
        String second = username();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicReference<Exception> rejected = new AtomicReference<>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var taskA = pool.submit(() -> attempt(first, citizenId, ready, start, succeeded, rejected));
            var taskB = pool.submit(() -> attempt(second, citizenId, ready, start, succeeded, rejected));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            taskA.get(15, TimeUnit.SECONDS);
            taskB.get(15, TimeUnit.SECONDS);
            assertEquals(1, succeeded.get());
            assertTrue(rejected.get() instanceof DataIntegrityViolationException
                    || rejected.get() instanceof BusinessException
                    || rejected.get() instanceof PessimisticLockingFailureException,
                    () -> "Unexpected rejection type: " + rejected.get().getClass().getSimpleName());
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM patient WHERE citizen_id = ?",
                    Long.class, citizenId));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE username IN (?, ?)",
                    Long.class, first, second));
        } finally {
            jdbc.update("DELETE FROM patient WHERE citizen_id = ?", citizenId);
            jdbc.update("DELETE FROM user_account WHERE username IN (?, ?)", first, second);
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE username IN (?, ?)",
                Long.class, first, second));
    }

    private void attempt(String username, String citizenId, CountDownLatch ready, CountDownLatch start,
            AtomicInteger succeeded, AtomicReference<Exception> rejected) {
        ready.countDown();
        try {
            start.await();
            registrations.register(request(username, citizenId, null));
            succeeded.incrementAndGet();
        } catch (Exception failure) {
            rejected.compareAndSet(null, failure);
        }
    }

    private long seedPatient(Long userId, String citizenId) {
        String code = "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        jdbc.update("INSERT INTO patient (patient_code, user_id, citizen_id, full_name, date_of_birth, gender) "
                + "VALUES (?, ?, ?, ?, ?, ?)", code, userId, citizenId, "Synthetic Patient",
                LocalDate.of(1990, 1, 2), "OTHER");
        return jdbc.queryForObject("SELECT id FROM patient WHERE patient_code = ?", Long.class, code);
    }

    private RegisterPatientRequest request(String username, String citizenId, String verificationToken) {
        return new RegisterPatientRequest(username, UUID.randomUUID().toString(), "Synthetic Patient",
                "0000000000", citizenId, LocalDate.of(1990, 1, 2), PatientGender.OTHER, null,
                verificationToken);
    }

    private String username() {
        return "pat_" + UUID.randomUUID().toString().substring(0, 12);
    }

    private String citizenId() {
        long number = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L);
        return "%012d".formatted(number);
    }
}
