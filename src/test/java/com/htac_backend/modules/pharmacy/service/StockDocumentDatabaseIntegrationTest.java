package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.pharmacy.dto.MedicineBatchWriteRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineWriteRequest;
import com.htac_backend.modules.pharmacy.entity.MedicineBatch;
import com.htac_backend.modules.pharmacy.entity.StockDocument;
import com.htac_backend.modules.pharmacy.entity.StockDocumentItem;
import com.htac_backend.modules.pharmacy.repository.MedicineBatchRepository;
import com.htac_backend.modules.pharmacy.repository.StockDocumentItemRepository;
import com.htac_backend.modules.pharmacy.repository.StockDocumentRepository;
import com.htac_backend.shared.api.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class StockDocumentDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired MedicineCatalogService medicines;
    @Autowired MedicineBatchService batches;
    @Autowired MedicineBatchRepository batchRepository;
    @Autowired StockDocumentRepository documents;
    @Autowired StockDocumentItemRepository items;
    @Autowired StockDocumentService posting;
    @Autowired StockBalanceService balances;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void postedOnlyIdempotentAtomicAndConcurrentBalance() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 12);
        UserAccount pharmacist = accounts.saveAndFlush(new UserAccount("s" + tag,
                "{noop}unused", "Synthetic staff", "0000000000", AccountRole.PHARMACIST));
        authenticate(pharmacist);
        List<Long> documentIds = new ArrayList<>();
        Long medicineId = null;
        Long batchId = null;
        try {
            medicineId = medicines.create(new MedicineWriteRequest("M" + tag, "Synthetic medicine",
                    "Synthetic ingredient", "1 mg", "tablet", "tablet", BigDecimal.ZERO, 0, true)).id();
            batchId = batches.create(new MedicineBatchWriteRequest(medicineId, "B" + tag,
                    batches.businessDate().plusYears(1), "ACTIVE", null)).id();
            Long secondBatchId = batches.create(new MedicineBatchWriteRequest(medicineId, "C" + tag,
                    batches.businessDate().plusYears(1), "ACTIVE", null)).id();
            MedicineBatch batch = batchRepository.findDetail(batchId).orElseThrow();
            MedicineBatch secondBatch = batchRepository.findDetail(secondBatchId).orElseThrow();
            var opening = draft(tag + "A", "OPENING", "MAIN", "AVAILABLE", null, null,
                    batch, "10", documentIds);
            assertEquals(0, balance(batchId, "MAIN", "AVAILABLE").compareTo(BigDecimal.ZERO));
            var firstPost = posting.post(opening.getId());
            assertNotNull(firstPost.postedAt());
            assertEquals(firstPost.postedAt(), posting.post(opening.getId()).postedAt());
            assertEquals(0, balance(batchId, "MAIN", "AVAILABLE").compareTo(new BigDecimal("10")));
            assertEquals("STOCK_DOCUMENT_FINAL", assertThrows(BusinessException.class,
                    () -> posting.cancel(opening.getId(), "Cannot rewrite history")).getCode());

            var multiline = draft(tag + "E", "TRANSFER", "MAIN", "AVAILABLE", "COUNTER",
                    "AVAILABLE", batch, "1", documentIds);
            items.saveAndFlush(new StockDocumentItem(multiline, secondBatch, BigDecimal.ONE,
                    null, null, null, null, null, null));
            assertEquals("INSUFFICIENT_STOCK", assertThrows(BusinessException.class,
                    () -> posting.post(multiline.getId())).getCode());
            assertEquals("DRAFT", documents.findById(multiline.getId()).orElseThrow().getStatus());
            assertEquals(0, balance(batchId, "MAIN", "AVAILABLE").compareTo(new BigDecimal("10")));
            assertEquals(0, balance(batchId, "COUNTER", "AVAILABLE").compareTo(BigDecimal.ZERO));

            var invalid = draft(tag + "B", "TRANSFER", "MAIN", "AVAILABLE", "COUNTER",
                    "AVAILABLE", batch, "11", documentIds);
            assertEquals("INSUFFICIENT_STOCK", assertThrows(BusinessException.class,
                    () -> posting.post(invalid.getId())).getCode());
            assertEquals("DRAFT", documents.findById(invalid.getId()).orElseThrow().getStatus());
            assertNull(documents.findById(invalid.getId()).orElseThrow().getPostedAt());
            assertEquals(0, balance(batchId, "COUNTER", "AVAILABLE").compareTo(BigDecimal.ZERO));
            posting.cancel(invalid.getId(), "Invalid count");
            assertEquals("STOCK_DOCUMENT_FINAL", assertThrows(BusinessException.class,
                    () -> posting.post(invalid.getId())).getCode());

            var transferOne = draft(tag + "C", "TRANSFER", "MAIN", "AVAILABLE", "COUNTER",
                    "AVAILABLE", batch, "7", documentIds);
            var transferTwo = draft(tag + "D", "TRANSFER", "MAIN", "AVAILABLE", "COUNTER",
                    "AVAILABLE", batch, "7", documentIds);
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var a = executor.submit(() -> concurrentPost(pharmacist, transferOne.getId(), start));
                var b = executor.submit(() -> concurrentPost(pharmacist, transferTwo.getId(), start));
                start.countDown();
                assertEquals(1, (a.get(20, TimeUnit.SECONDS) ? 1 : 0)
                        + (b.get(20, TimeUnit.SECONDS) ? 1 : 0));
            }
            assertEquals(0, balance(batchId, "MAIN", "AVAILABLE").compareTo(new BigDecimal("3")));
            assertEquals(0, balance(batchId, "COUNTER", "AVAILABLE").compareTo(new BigDecimal("7")));
        } finally {
            SecurityContextHolder.clearContext();
            for (Long id : documentIds) {
                jdbc.update("DELETE FROM stock_document_item WHERE document_id = ?", id);
                jdbc.update("DELETE FROM stock_document WHERE id = ?", id);
            }
            jdbc.update("DELETE FROM medicine_batch WHERE batch_number IN (?, ?)", "B" + tag, "C" + tag);
            jdbc.update("DELETE FROM medicine WHERE code = ?", "M" + tag);
            accounts.deleteById(pharmacist.getId());
        }
    }

    private StockDocument draft(String code, String type, String location, String bucket,
            String destination, String destinationBucket, MedicineBatch batch, String quantity,
            List<Long> ids) {
        StockDocument document = documents.saveAndFlush(new StockDocument("S" + code, type,
                Instant.now(), location, bucket, destination, destinationBucket,
                null, null, null, null));
        ids.add(document.getId());
        items.saveAndFlush(new StockDocumentItem(document, batch, new BigDecimal(quantity),
                null, null, null, null, null, null));
        return document;
    }

    private boolean concurrentPost(UserAccount pharmacist, Long id, CountDownLatch start) {
        authenticate(pharmacist);
        try {
            start.await(10, TimeUnit.SECONDS);
            posting.post(id);
            return true;
        } catch (BusinessException e) {
            assertEquals("INSUFFICIENT_STOCK", e.getCode());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private BigDecimal balance(Long batchId, String location, String bucket) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> balances.balance(new StockPosition(batchId, location, bucket)));
    }

    private void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
