package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.pharmacy.dto.DispenseLineRequest;
import com.htac_backend.modules.pharmacy.dto.DispenseStockRequest;
import com.htac_backend.modules.pharmacy.dto.InboundStockLineRequest;
import com.htac_backend.modules.pharmacy.dto.OpeningStockRequest;
import com.htac_backend.modules.pharmacy.dto.PatientReturnLineRequest;
import com.htac_backend.modules.pharmacy.dto.PatientReturnRequest;
import com.htac_backend.shared.api.BusinessException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class PatientReturnDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired InboundStockService inbound;
    @Autowired DispenseStockService dispensing;
    @Autowired PatientReturnService returns;
    @Autowired StockDocumentService documents;
    @Autowired StockBalanceService balances;
    @Autowired MedicineBatchService batches;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void returnsTracePostedSourceAndConcurrentPostsCannotOverreturn() throws Exception {
        try (var f = new PharmacyPrescriptionTestFixture(jdbc, accounts)) {
            long medicine = f.medicine("1");
            var today = batches.businessDate();
            long batch = f.batch(medicine, "1", today.plusYears(1));
            long wrongBatch = f.batch(medicine, "2", today.plusYears(1));
            long prescription = f.prescription(medicine, new BigDecimal("10"), "ISSUED");
            long line = f.prescriptionItem(prescription);
            f.authenticate(f.pharmacist);
            documents.post(inbound.opening(new OpeningStockRequest(today, "COUNTER", "AVAILABLE",
                    null, List.of(new InboundStockLineRequest(batch, new BigDecimal("10"),
                            null, null)))).id());
            var dispense = dispensing.create(new DispenseStockRequest(prescription,
                    OffsetDateTime.now(), List.of(new DispenseLineRequest(line, batch,
                    new BigDecimal("6")))));
            long sourceId = dispense.items().getFirst().id();
            assertEquals("PATIENT_RETURN_SOURCE_INVALID", assertThrows(BusinessException.class,
                    () -> returns.create(request(prescription, sourceId, line, batch, "1")))
                    .getCode());
            documents.post(dispense.id());
            assertEquals("PATIENT_RETURN_SOURCE_MISMATCH", assertThrows(BusinessException.class,
                    () -> returns.create(request(prescription, sourceId, line, wrongBatch, "1")))
                    .getCode());
            assertEquals("PATIENT_RETURN_EXCEEDS_DISPENSE", assertThrows(BusinessException.class,
                    () -> returns.create(request(prescription, sourceId, line, batch, "7")))
                    .getCode());

            var first = returns.create(request(prescription, sourceId, line, batch, "4"));
            var second = returns.create(request(prescription, sourceId, line, batch, "4"));
            SecurityContextHolder.clearContext();
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var a = executor.submit(() -> attempt(f, first.id(), start));
                var b = executor.submit(() -> attempt(f, second.id(), start));
                start.countDown();
                assertEquals(1, (a.get(20, TimeUnit.SECONDS) ? 1 : 0)
                        + (b.get(20, TimeUnit.SECONDS) ? 1 : 0));
            }
            f.authenticate(f.pharmacist);
            assertQuantity(batch, "AVAILABLE", "4");
            assertQuantity(batch, "QUARANTINE", "4");
            var rest = returns.create(request(prescription, sourceId, line, batch, "2"));
            documents.post(rest.id());
            assertQuantity(batch, "QUARANTINE", "6");
            assertEquals(0, dispensing.snapshot(prescription).items().getFirst()
                    .remainingQuantity().compareTo(new BigDecimal("4")));
            assertEquals("DISPENSE_EXCEEDS_PRESCRIPTION", assertThrows(BusinessException.class,
                    () -> dispensing.create(new DispenseStockRequest(prescription,
                            OffsetDateTime.now(), List.of(new DispenseLineRequest(line, batch,
                            new BigDecimal("5")))))).getCode());
        }
    }

    private PatientReturnRequest request(long prescription, long source, long line,
            long batch, String quantity) {
        return new PatientReturnRequest(prescription, OffsetDateTime.now(), "Patient returned medicine",
                List.of(new PatientReturnLineRequest(source, line, batch,
                        new BigDecimal(quantity), "SEALED", "Intact package")));
    }

    private boolean attempt(PharmacyPrescriptionTestFixture f, Long id, CountDownLatch start) {
        f.authenticate(f.pharmacist);
        try {
            start.await(10, TimeUnit.SECONDS);
            documents.post(id);
            return true;
        } catch (BusinessException e) {
            assertEquals("PATIENT_RETURN_EXCEEDS_DISPENSE", e.getCode());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertQuantity(long batch, String bucket, String expected) {
        BigDecimal actual = new TransactionTemplate(transactions).execute(status ->
                balances.balance(new StockPosition(batch, "COUNTER", bucket)));
        assertEquals(0, actual.compareTo(new BigDecimal(expected)));
    }
}
