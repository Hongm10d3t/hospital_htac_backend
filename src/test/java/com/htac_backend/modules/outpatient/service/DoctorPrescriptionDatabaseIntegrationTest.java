package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.outpatient.dto.CreatePrescriptionRequest;
import com.htac_backend.modules.outpatient.dto.PrescriptionLineRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class DoctorPrescriptionDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired DoctorVisitService doctorVisits;
    @Autowired DoctorPrescriptionService prescriptions;
    @Autowired IssuedPrescriptionService dispensing;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void draftAndIssuedContractRespectMedicineAndStockBoundaries() {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            long medicineId = f.medicine("1", true);
            f.authenticate(f.doctorA);
            doctorVisits.start(f.visitA);
            var draft = prescriptions.create(f.visitA, request(medicineId, "2.500"));
            assertEquals("DRAFT", draft.status().name());
            assertNull(draft.issuedAt());
            assertEquals("Synthetic medicine 1", draft.items().getFirst().medicineName());
            assertEquals(1, prescriptions.forVisit(f.visitA).size());
            assertEquals(0L, f.id("SELECT COUNT(*) FROM stock_document WHERE prescription_id = ?", draft.id()));

            f.authenticate(f.pharmacist);
            var tx = new TransactionTemplate(transactionManager);
            assertEquals("PRESCRIPTION_NOT_ISSUED", assertThrows(BusinessException.class,
                    () -> tx.execute(status -> dispensing.lockForDispensing(draft.id()))).getCode());
            f.authenticate(f.doctorA);
            var issued = prescriptions.issue(draft.id());
            assertEquals("ISSUED", issued.status().name());
            assertNotNull(issued.issuedAt());
            assertEquals(issued.issuedAt(), prescriptions.issue(draft.id()).issuedAt());
            assertEquals(0L, f.id("SELECT COUNT(*) FROM stock_document WHERE prescription_id = ?", draft.id()));
            f.authenticate(f.pharmacist);
            var snapshot = tx.execute(status -> dispensing.lockForDispensing(draft.id()));
            assertEquals(new BigDecimal("2.500"), snapshot.items().getFirst().quantity());
            f.authenticate(f.doctorB);
            assertThrows(NotFoundException.class, () -> prescriptions.get(draft.id()));
            assertThrows(NotFoundException.class, () -> prescriptions.create(f.visitA, request(medicineId, "1")));
        }
    }

    @Test
    void duplicateInactiveAndInvalidQuantitiesAreRejected() {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            long activeId = f.medicine("1", true);
            long inactiveId = f.medicine("2", false);
            f.authenticate(f.doctorA);
            doctorVisits.start(f.visitA);
            assertThrows(InvalidRequestException.class, () -> prescriptions.create(f.visitA,
                    new CreatePrescriptionRequest(null, List.of(
                            new PrescriptionLineRequest(activeId, BigDecimal.ONE, "Use"),
                            new PrescriptionLineRequest(activeId, BigDecimal.ONE, "Use")))));
            assertThrows(InvalidRequestException.class, () -> prescriptions.create(f.visitA,
                    request(activeId, "0")));
            assertEquals("MEDICINE_INACTIVE", assertThrows(BusinessException.class,
                    () -> prescriptions.create(f.visitA, request(inactiveId, "1"))).getCode());
            var draft = prescriptions.create(f.visitA, request(activeId, "1"));
            jdbc.update("UPDATE medicine SET is_active = FALSE WHERE id = ?", activeId);
            assertEquals("MEDICINE_INACTIVE", assertThrows(BusinessException.class,
                    () -> prescriptions.issue(draft.id())).getCode());
            assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM prescription WHERE id = ?",
                    String.class, draft.id()));
        }
    }

    @Test
    void simultaneousIssueCallsKeepOneIssuedTimestamp() throws Exception {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            long medicineId = f.medicine("1", true);
            f.authenticate(f.doctorA);
            doctorVisits.start(f.visitA);
            long prescriptionId = prescriptions.create(f.visitA, request(medicineId, "1")).id();
            SecurityContextHolder.clearContext();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> issue(f, prescriptionId, ready, start));
                var b = pool.submit(() -> issue(f, prescriptionId, ready, start));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                Instant first = a.get(15, TimeUnit.SECONDS);
                Instant second = b.get(15, TimeUnit.SECONDS);
                assertEquals(first, second);
            }
            assertEquals(1L, f.id("SELECT COUNT(*) FROM prescription WHERE id = ?", prescriptionId));
        }
    }

    private Instant issue(OutpatientTestFixture f, long prescriptionId, CountDownLatch ready,
            CountDownLatch start) {
        f.authenticate(f.doctorA);
        ready.countDown();
        try {
            try {
                start.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            }
            return prescriptions.issue(prescriptionId).issuedAt();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private CreatePrescriptionRequest request(long medicineId, String quantity) {
        return new CreatePrescriptionRequest("Synthetic note", List.of(
                new PrescriptionLineRequest(medicineId, new BigDecimal(quantity), "Synthetic instruction")));
    }
}
