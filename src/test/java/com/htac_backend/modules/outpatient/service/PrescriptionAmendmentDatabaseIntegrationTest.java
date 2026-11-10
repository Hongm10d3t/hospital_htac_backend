package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.outpatient.dto.CreatePrescriptionRequest;
import com.htac_backend.modules.outpatient.dto.PrescriptionLineRequest;
import com.htac_backend.modules.outpatient.dto.UpdateClinicalVisitRequest;
import com.htac_backend.modules.outpatient.dto.UpdatePrescriptionRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class PrescriptionAmendmentDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired DoctorVisitService doctorVisits;
    @Autowired ClinicalVisitService clinical;
    @Autowired DoctorPrescriptionService prescriptions;
    @Autowired DoctorPrescriptionAmendmentService amendments;
    @Autowired IssuedPrescriptionService dispensing;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void draftCanBeReplacedAndSupplementalPrescriptionsKeepSameVisit() {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            long firstMedicine = f.medicine("1", true);
            long secondMedicine = f.medicine("2", true);
            f.authenticate(f.doctorA);
            doctorVisits.start(f.visitA);
            var first = prescriptions.create(f.visitA, request(firstMedicine));
            var revised = amendments.update(first.id(), new UpdatePrescriptionRequest("Revised",
                    List.of(new PrescriptionLineRequest(secondMedicine, new BigDecimal("3"), "New instruction"))));
            assertEquals(1, revised.items().size());
            assertEquals(secondMedicine, revised.items().getFirst().medicineId());
            assertEquals("Revised", revised.note());
            prescriptions.issue(first.id());
            assertEquals("PRESCRIPTION_IMMUTABLE", assertThrows(BusinessException.class,
                    () -> amendments.update(first.id(), new UpdatePrescriptionRequest(null,
                            List.of(new PrescriptionLineRequest(firstMedicine, BigDecimal.ONE, "Use")))))
                    .getCode());
            clinical.update(f.visitA, new UpdateClinicalVisitRequest(null, null,
                    "Synthetic diagnosis", null, null));
            clinical.complete(f.visitA);
            var extra = prescriptions.create(f.visitA, request(firstMedicine));
            assertEquals(f.visitA, extra.visitId());
            assertEquals(f.doctorA.getId(), extra.doctorId());
            assertEquals(2, prescriptions.forVisit(f.visitA).size());
        }
    }

    @Test
    void cancellationNeedsReasonAndProtectsIssuedAndStockReferences() {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            long medicineId = f.medicine("1", true);
            f.authenticate(f.doctorA);
            doctorVisits.start(f.visitA);
            var draft = prescriptions.create(f.visitA, request(medicineId));
            assertThrows(InvalidRequestException.class, () -> amendments.cancel(draft.id(), " "));
            prescriptions.issue(draft.id());
            assertEquals("ISSUED_PRESCRIPTION_EXISTS", assertThrows(BusinessException.class,
                    () -> clinical.notComplete(f.visitA, "Patient left")).getCode());
            var cancelled = amendments.cancel(draft.id(), "Order changed");
            assertEquals("CANCELLED", cancelled.status().name());
            assertEquals("CANCELLED", amendments.cancel(draft.id(), "Order changed").status().name());
            assertEquals("PRESCRIPTION_FINAL", assertThrows(BusinessException.class,
                    () -> amendments.cancel(draft.id(), "Different reason")).getCode());
            assertEquals("NOT_COMPLETED", clinical.notComplete(f.visitA, "Patient left").status().name());

            var second = prescriptions.create(f.historicVisit, request(medicineId));
            insertStockDocument(f, second.id(), "DRAFT");
            assertEquals("PRESCRIPTION_HAS_STOCK_DOCUMENT", assertThrows(BusinessException.class,
                    () -> amendments.cancel(second.id(), "Cannot fill")).getCode());
            assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM prescription WHERE id = ?",
                    String.class, second.id()));
        }
    }

    @Test
    void dispensingAndCancellationRaceLeavesOneValidOutcome() throws Exception {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            long medicineId = f.medicine("1", true);
            f.authenticate(f.doctorA);
            doctorVisits.start(f.visitA);
            long prescriptionId = prescriptions.create(f.visitA, request(medicineId)).id();
            prescriptions.issue(prescriptionId);
            SecurityContextHolder.clearContext();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger dispensed = new AtomicInteger();
            AtomicInteger cancelled = new AtomicInteger();
            try (var pool = Executors.newFixedThreadPool(2)) {
                var pharmacist = pool.submit(() -> attemptDispense(f, prescriptionId,
                        ready, start, dispensed));
                var doctor = pool.submit(() -> attemptCancel(f, prescriptionId,
                        ready, start, cancelled));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                pharmacist.get(15, TimeUnit.SECONDS);
                doctor.get(15, TimeUnit.SECONDS);
            }
            assertEquals(1, dispensed.get() + cancelled.get());
            String status = jdbc.queryForObject("SELECT status FROM prescription WHERE id = ?",
                    String.class, prescriptionId);
            long documents = f.id("SELECT COUNT(*) FROM stock_document WHERE prescription_id = ?",
                    prescriptionId);
            if (dispensed.get() == 1) {
                assertEquals("ISSUED", status);
                assertEquals(1L, documents);
            } else {
                assertEquals("CANCELLED", status);
                assertEquals(0L, documents);
            }
        }
    }

    private void attemptDispense(OutpatientTestFixture f, long prescriptionId,
            CountDownLatch ready, CountDownLatch start, AtomicInteger success) {
        f.authenticate(f.pharmacist);
        ready.countDown();
        try {
            await(start);
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            try {
                transaction.execute(status -> {
                    dispensing.lockForDispensing(prescriptionId);
                    insertStockDocument(f, prescriptionId, "POSTED");
                    return null;
                });
                success.incrementAndGet();
            } catch (BusinessException expected) {
                assertEquals("PRESCRIPTION_NOT_ISSUED", expected.getCode());
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void attemptCancel(OutpatientTestFixture f, long prescriptionId,
            CountDownLatch ready, CountDownLatch start, AtomicInteger success) {
        f.authenticate(f.doctorA);
        ready.countDown();
        try {
            await(start);
            try {
                amendments.cancel(prescriptionId, "No longer needed");
                success.incrementAndGet();
            } catch (BusinessException expected) {
                assertEquals("PRESCRIPTION_HAS_STOCK_DOCUMENT", expected.getCode());
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private void insertStockDocument(OutpatientTestFixture f, long prescriptionId, String status) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO stock_document (document_code, type, occurred_at, status, "
                + "stock_location, stock_bucket, prescription_id, posted_at, posted_by) "
                + "VALUES (?, 'DISPENSE', ?, ?, 'COUNTER', 'AVAILABLE', ?, ?, ?)",
                "S" + java.util.UUID.randomUUID().toString().substring(0, 12), Timestamp.from(now),
                status, prescriptionId, "POSTED".equals(status) ? Timestamp.from(now) : null,
                "POSTED".equals(status) ? f.pharmacist.getId() : null);
    }

    private CreatePrescriptionRequest request(long medicineId) {
        return new CreatePrescriptionRequest("Original", List.of(
                new PrescriptionLineRequest(medicineId, BigDecimal.ONE, "Synthetic instruction")));
    }
}
