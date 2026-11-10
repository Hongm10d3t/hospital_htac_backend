package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.pharmacy.dto.InboundStockLineRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineBatchWriteRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineWriteRequest;
import com.htac_backend.modules.pharmacy.dto.OpeningStockRequest;
import com.htac_backend.modules.pharmacy.dto.TransferStockRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class TransferStockDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired MedicineCatalogService medicines;
    @Autowired MedicineBatchService batches;
    @Autowired InboundStockService inbound;
    @Autowired TransferStockService transfers;
    @Autowired StockDocumentService documents;
    @Autowired StockBalanceService balances;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void transferConservesPhysicalStockAndReleaseChecksBatchAtPosting() {
        String tag = UUID.randomUUID().toString().substring(0, 12);
        UserAccount pharmacist = accounts.saveAndFlush(new UserAccount("t" + tag,
                "{noop}unused", "Synthetic staff", "0000000000", AccountRole.PHARMACIST));
        authenticate(pharmacist);
        List<Long> documentIds = new ArrayList<>();
        try {
            Long medicineId = medicines.create(new MedicineWriteRequest("M" + tag,
                    "Synthetic medicine", "Synthetic ingredient", "1 mg", "tablet", "tablet",
                    BigDecimal.ZERO, 0, true)).id();
            var expiry = batches.businessDate().plusYears(1);
            Long batchId = batches.create(new MedicineBatchWriteRequest(medicineId,
                    "B" + tag, expiry, "ACTIVE", null)).id();
            var opening = inbound.opening(new OpeningStockRequest(batches.businessDate(),
                    "MAIN", "AVAILABLE", null, List.of(line(batchId, "10"))));
            documentIds.add(opening.id());
            documents.post(opening.id());
            assertThrows(InvalidRequestException.class, () -> transfers.create(
                    transfer(batchId, "MAIN", "AVAILABLE", "MAIN", "AVAILABLE", "1", null, false)));
            var move = transfers.create(transfer(batchId, "MAIN", "AVAILABLE", "COUNTER",
                    "AVAILABLE", "4", null, false));
            documentIds.add(move.id());
            documents.post(move.id());
            assertQuantity(batchId, "MAIN", "AVAILABLE", "6");
            assertQuantity(batchId, "COUNTER", "AVAILABLE", "4");

            var quarantine = transfers.create(transfer(batchId, "COUNTER", "AVAILABLE",
                    "COUNTER", "QUARANTINE", "2", "Damaged packaging", false));
            documentIds.add(quarantine.id());
            documents.post(quarantine.id());
            assertQuantity(batchId, "COUNTER", "AVAILABLE", "2");
            assertQuantity(batchId, "COUNTER", "QUARANTINE", "2");
            assertThrows(InvalidRequestException.class, () -> transfers.create(
                    transfer(batchId, "COUNTER", "QUARANTINE", "COUNTER", "AVAILABLE",
                            "1", "Reviewed", false)));
            var release = transfers.create(transfer(batchId, "COUNTER", "QUARANTINE",
                    "COUNTER", "AVAILABLE", "1", "Reviewed", true));
            documentIds.add(release.id());
            batches.update(batchId, new MedicineBatchWriteRequest(medicineId,
                    "B" + tag, expiry, "BLOCKED", "Safety review"));
            assertEquals("BATCH_NOT_RELEASABLE", assertThrows(BusinessException.class,
                    () -> documents.post(release.id())).getCode());
            assertQuantity(batchId, "COUNTER", "QUARANTINE", "2");
            batches.update(batchId, new MedicineBatchWriteRequest(medicineId,
                    "B" + tag, expiry, "ACTIVE", null));
            documents.post(release.id());
            assertQuantity(batchId, "COUNTER", "AVAILABLE", "3");
            assertQuantity(batchId, "COUNTER", "QUARANTINE", "1");
        } finally {
            SecurityContextHolder.clearContext();
            for (Long id : documentIds) {
                jdbc.update("DELETE FROM stock_document_item WHERE document_id = ?", id);
                jdbc.update("DELETE FROM stock_document WHERE id = ?", id);
            }
            jdbc.update("DELETE FROM medicine_batch WHERE batch_number = ?", "B" + tag);
            jdbc.update("DELETE FROM medicine WHERE code = ?", "M" + tag);
            accounts.deleteById(pharmacist.getId());
        }
    }

    private InboundStockLineRequest line(Long batchId, String quantity) {
        return new InboundStockLineRequest(batchId, new BigDecimal(quantity), null, null);
    }

    private TransferStockRequest transfer(Long batchId, String sourceLocation, String sourceBucket,
            String destinationLocation, String destinationBucket, String quantity,
            String note, boolean confirmed) {
        return new TransferStockRequest(OffsetDateTime.now(), sourceLocation, sourceBucket,
                destinationLocation, destinationBucket, note, confirmed,
                List.of(line(batchId, quantity)));
    }

    private void assertQuantity(Long batchId, String location, String bucket, String expected) {
        BigDecimal actual = new TransactionTemplate(transactionManager).execute(status ->
                balances.balance(new StockPosition(batchId, location, bucket)));
        assertEquals(0, actual.compareTo(new BigDecimal(expected)));
    }

    private void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
