package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.pharmacy.dto.DispenseLineRequest;
import com.htac_backend.modules.pharmacy.dto.DispenseStockRequest;
import com.htac_backend.modules.pharmacy.dto.InboundStockLineRequest;
import com.htac_backend.modules.pharmacy.dto.OpeningStockRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineBatchWriteRequest;
import com.htac_backend.modules.pharmacy.dto.TransferStockRequest;
import com.htac_backend.modules.outpatient.service.DoctorPrescriptionAmendmentService;
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
class DispenseStockDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired InboundStockService inbound;
    @Autowired DispenseStockService dispensing;
    @Autowired StockDocumentService documents;
    @Autowired StockBalanceService balances;
    @Autowired MedicineBatchService batches;
    @Autowired TransferStockService transfers;
    @Autowired DoctorPrescriptionAmendmentService amendments;
    @Autowired com.htac_backend.modules.administration.repository.UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void multipleBatchesAndConcurrentPostsCannotExceedPrescription() throws Exception {
        try (var f = new PharmacyPrescriptionTestFixture(jdbc, accounts)) {
            long medicine = f.medicine("1");
            long otherMedicine = f.medicine("2");
            long firstBatch = f.batch(medicine, "1", java.time.LocalDate.now(
                    java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusYears(1));
            long secondBatch = f.batch(medicine, "2", java.time.LocalDate.now(
                    java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusYears(1));
            long wrongBatch = f.batch(otherMedicine, "3", java.time.LocalDate.now(
                    java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusYears(1));
            long prescription = f.prescription(medicine, new BigDecimal("10"), "ISSUED");
            long item = f.prescriptionItem(prescription);
            long draftPrescription = f.prescription(medicine, BigDecimal.ONE, "DRAFT");
            f.authenticate(f.pharmacist);
            var opening = inbound.opening(new OpeningStockRequest(java.time.LocalDate.now(
                    java.time.ZoneId.of("Asia/Ho_Chi_Minh")), "COUNTER", "AVAILABLE", null,
                    List.of(new InboundStockLineRequest(firstBatch, new BigDecimal("10"), null, null),
                            new InboundStockLineRequest(secondBatch, new BigDecimal("10"), null, null))));
            documents.post(opening.id());
            assertEquals("PRESCRIPTION_NOT_ISSUED", assertThrows(BusinessException.class,
                    () -> dispensing.create(request(draftPrescription,
                            new DispenseLineRequest(f.prescriptionItem(draftPrescription), firstBatch,
                                    BigDecimal.ONE)))).getCode());
            assertEquals("DISPENSE_LINE_MISMATCH", assertThrows(BusinessException.class,
                    () -> dispensing.create(request(prescription,
                            new DispenseLineRequest(item, wrongBatch, BigDecimal.ONE)))).getCode());

            var first = dispensing.create(request(prescription,
                    new DispenseLineRequest(item, firstBatch, new BigDecimal("4")),
                    new DispenseLineRequest(item, secondBatch, new BigDecimal("2"))));
            assertEquals(2, first.items().size());
            assertEquals(0, balance(firstBatch).compareTo(new BigDecimal("10")));
            documents.post(first.id());
            assertEquals(0, balance(firstBatch).compareTo(new BigDecimal("6")));
            assertEquals(0, balance(secondBatch).compareTo(new BigDecimal("8")));
            assertEquals(0, dispensing.snapshot(prescription).items().getFirst()
                    .remainingQuantity().compareTo(new BigDecimal("4")));

            var second = dispensing.create(request(prescription,
                    new DispenseLineRequest(item, firstBatch, new BigDecimal("4"))));
            var third = dispensing.create(request(prescription,
                    new DispenseLineRequest(item, secondBatch, new BigDecimal("4"))));
            SecurityContextHolder.clearContext();
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var a = executor.submit(() -> attempt(f, second.id(), start));
                var b = executor.submit(() -> attempt(f, third.id(), start));
                start.countDown();
                assertEquals(1, (a.get(20, TimeUnit.SECONDS) ? 1 : 0)
                        + (b.get(20, TimeUnit.SECONDS) ? 1 : 0));
            }
            f.authenticate(f.pharmacist);
            assertEquals(0, dispensing.snapshot(prescription).items().getFirst()
                    .remainingQuantity().compareTo(BigDecimal.ZERO));
            assertEquals("DISPENSE_EXCEEDS_PRESCRIPTION", assertThrows(BusinessException.class,
                    () -> dispensing.create(request(prescription,
                            new DispenseLineRequest(item, firstBatch, BigDecimal.ONE)))).getCode());
        }
    }

    @Test
    void blockedExpiredQuarantineAndStaleDraftAreRejectedAtomically() {
        try (var f = new PharmacyPrescriptionTestFixture(jdbc, accounts)) {
            long medicine = f.medicine("1");
            var today = batches.businessDate();
            long firstBatch = f.batch(medicine, "1", today.plusYears(1));
            long secondBatch = f.batch(medicine, "2", today.plusYears(1));
            long expiredBatch = f.batch(medicine, "3", today);
            long quarantineBatch = f.batch(medicine, "4", today.plusYears(1));
            long prescription = f.prescription(medicine, new BigDecimal("5"), "ISSUED");
            long item = f.prescriptionItem(prescription);
            f.authenticate(f.pharmacist);
            documents.post(inbound.opening(new OpeningStockRequest(today, "COUNTER", "AVAILABLE",
                    null, List.of(new InboundStockLineRequest(firstBatch, new BigDecimal("2"), null, null),
                            new InboundStockLineRequest(secondBatch, new BigDecimal("2"), null, null),
                            new InboundStockLineRequest(expiredBatch, BigDecimal.ONE, null, null)))).id());
            documents.post(inbound.opening(new OpeningStockRequest(today, "COUNTER", "QUARANTINE",
                    null, List.of(new InboundStockLineRequest(quarantineBatch, BigDecimal.ONE,
                            null, null)))).id());
            batches.update(firstBatch, new MedicineBatchWriteRequest(medicine, "b1" + f.tag,
                    today.plusYears(1), "BLOCKED", "Safety review"));
            assertEquals("BATCH_NOT_DISPENSABLE", assertThrows(BusinessException.class,
                    () -> dispensing.create(request(prescription,
                            new DispenseLineRequest(item, firstBatch, BigDecimal.ONE)))).getCode());
            assertEquals("BATCH_NOT_DISPENSABLE", assertThrows(BusinessException.class,
                    () -> dispensing.create(request(prescription,
                            new DispenseLineRequest(item, expiredBatch, BigDecimal.ONE)))).getCode());
            assertEquals("INSUFFICIENT_STOCK", assertThrows(BusinessException.class,
                    () -> dispensing.create(request(prescription,
                            new DispenseLineRequest(item, quarantineBatch, BigDecimal.ONE)))).getCode());
            batches.update(firstBatch, new MedicineBatchWriteRequest(medicine, "b1" + f.tag,
                    today.plusYears(1), "ACTIVE", null));
            var draft = dispensing.create(request(prescription,
                    new DispenseLineRequest(item, firstBatch, BigDecimal.ONE)));
            batches.update(firstBatch, new MedicineBatchWriteRequest(medicine, "b1" + f.tag,
                    today.plusYears(1), "BLOCKED", "Safety review"));
            assertEquals("BATCH_NOT_DISPENSABLE", assertThrows(BusinessException.class,
                    () -> documents.post(draft.id())).getCode());
            batches.update(firstBatch, new MedicineBatchWriteRequest(medicine, "b1" + f.tag,
                    today.plusYears(1), "ACTIVE", null));
            var multiline = dispensing.create(request(prescription,
                    new DispenseLineRequest(item, firstBatch, BigDecimal.ONE),
                    new DispenseLineRequest(item, secondBatch, BigDecimal.ONE)));
            var move = transfers.create(new TransferStockRequest(OffsetDateTime.now(),
                    "COUNTER", "AVAILABLE", "MAIN", "AVAILABLE", null, false,
                    List.of(new InboundStockLineRequest(secondBatch, new BigDecimal("2"), null, null))));
            documents.post(move.id());
            assertEquals("INSUFFICIENT_STOCK", assertThrows(BusinessException.class,
                    () -> documents.post(multiline.id())).getCode());
            assertEquals("DRAFT", documents.get(multiline.id()).status());
            assertEquals(0, balance(firstBatch).compareTo(new BigDecimal("2")));
        }
    }

    @Test
    void cancellationAndRealDispenseLeaveOneValidOutcome() throws Exception {
        try (var f = new PharmacyPrescriptionTestFixture(jdbc, accounts)) {
            long medicine = f.medicine("1");
            var today = batches.businessDate();
            long batch = f.batch(medicine, "1", today.plusYears(1));
            long prescription = f.prescription(medicine, BigDecimal.ONE, "ISSUED");
            long item = f.prescriptionItem(prescription);
            f.authenticate(f.pharmacist);
            documents.post(inbound.opening(new OpeningStockRequest(today, "COUNTER", "AVAILABLE",
                    null, List.of(new InboundStockLineRequest(batch, BigDecimal.ONE,
                            null, null)))).id());
            SecurityContextHolder.clearContext();
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var dispensingFuture = executor.submit(() -> {
                    f.authenticate(f.pharmacist);
                    try {
                        start.await(10, TimeUnit.SECONDS);
                        var draft = dispensing.create(request(prescription,
                                new DispenseLineRequest(item, batch, BigDecimal.ONE)));
                        documents.post(draft.id());
                        return true;
                    } catch (BusinessException e) {
                        assertEquals("PRESCRIPTION_NOT_ISSUED", e.getCode());
                        return false;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                });
                var cancellationFuture = executor.submit(() -> {
                    f.authenticate(f.doctor);
                    try {
                        start.await(10, TimeUnit.SECONDS);
                        amendments.cancel(prescription, "Order changed");
                        return true;
                    } catch (BusinessException e) {
                        assertEquals("PRESCRIPTION_HAS_STOCK_DOCUMENT", e.getCode());
                        return false;
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                });
                start.countDown();
                boolean issued = dispensingFuture.get(20, TimeUnit.SECONDS);
                boolean cancelled = cancellationFuture.get(20, TimeUnit.SECONDS);
                assertEquals(1, (issued ? 1 : 0) + (cancelled ? 1 : 0));
                assertEquals(issued ? "ISSUED" : "CANCELLED",
                        jdbc.queryForObject("SELECT status FROM prescription WHERE id = ?",
                                String.class, prescription));
                assertEquals(issued ? 0 : 1, balance(batch).intValue());
            }
        }
    }

    private DispenseStockRequest request(long prescription, DispenseLineRequest... lines) {
        return new DispenseStockRequest(prescription, OffsetDateTime.now(), List.of(lines));
    }

    private boolean attempt(PharmacyPrescriptionTestFixture f, Long id, CountDownLatch start) {
        f.authenticate(f.pharmacist);
        try {
            start.await(10, TimeUnit.SECONDS);
            documents.post(id);
            return true;
        } catch (BusinessException e) {
            assertEquals("DISPENSE_EXCEEDS_PRESCRIPTION", e.getCode());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private BigDecimal balance(long batchId) {
        return new TransactionTemplate(transactions).execute(status ->
                balances.balance(new StockPosition(batchId, "COUNTER", "AVAILABLE")));
    }
}
