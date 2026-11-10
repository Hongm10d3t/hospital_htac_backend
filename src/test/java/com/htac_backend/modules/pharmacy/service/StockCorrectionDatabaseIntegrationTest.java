package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.pharmacy.dto.AdjustmentLineRequest;
import com.htac_backend.modules.pharmacy.dto.AdjustmentRequest;
import com.htac_backend.modules.pharmacy.dto.InboundStockLineRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineBatchWriteRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineWriteRequest;
import com.htac_backend.modules.pharmacy.dto.OpeningStockRequest;
import com.htac_backend.modules.pharmacy.dto.StockExitLineRequest;
import com.htac_backend.modules.pharmacy.dto.StockExitRequest;
import com.htac_backend.modules.pharmacy.dto.SupplierWriteRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
class StockCorrectionDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired MedicineCatalogService medicines;
    @Autowired MedicineBatchService batches;
    @Autowired SupplierCatalogService suppliers;
    @Autowired InboundStockService inbound;
    @Autowired StockCorrectionService corrections;
    @Autowired StockDocumentService documents;
    @Autowired StockBalanceService balances;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void supplierReturnDisposalAndCountAdjustmentsFollowPostedBalance() {
        String tag = UUID.randomUUID().toString().substring(0, 12);
        UserAccount pharmacist = accounts.saveAndFlush(new UserAccount("c" + tag,
                "{noop}unused", "Synthetic staff", "0000000000", AccountRole.PHARMACIST));
        authenticate(pharmacist);
        try {
            Long medicineId = medicines.create(new MedicineWriteRequest("M" + tag,
                    "Synthetic medicine", "Synthetic ingredient", "1 mg", "tablet", "tablet",
                    BigDecimal.ZERO, 0, true)).id();
            Long batchId = batches.create(new MedicineBatchWriteRequest(medicineId, "B" + tag,
                    batches.businessDate().plusYears(1), "ACTIVE", null)).id();
            Long supplierId = suppliers.create(new SupplierWriteRequest("S" + tag,
                    "Synthetic supplier", null, null, true)).id();
            var opening = inbound.opening(new OpeningStockRequest(batches.businessDate(),
                    "MAIN", "AVAILABLE", null,
                    List.of(new InboundStockLineRequest(batchId, new BigDecimal("10"), null, null))));
            documents.post(opening.id());
            assertThrows(InvalidRequestException.class, () -> corrections.supplierReturn(
                    exit(batchId, "1", null, "Return")));
            assertThrows(InvalidRequestException.class, () -> corrections.disposal(
                    exit(batchId, "1", null, " ")));
            var negative = corrections.adjustment(adjust(batchId, "10", "8"));
            assertEquals(0, negative.items().getFirst().quantity()
                    .compareTo(new BigDecimal("-2")));
            documents.post(negative.id());
            assertQuantity(batchId, "8");
            var returned = corrections.supplierReturn(exit(batchId, "2", supplierId,
                    "Return to supplier"));
            documents.post(returned.id());
            assertQuantity(batchId, "6");
            var disposal = corrections.disposal(exit(batchId, "1", null,
                    "Expired packaging"));
            documents.post(disposal.id());
            assertQuantity(batchId, "5");
            var positive = corrections.adjustment(adjust(batchId, "5", "7"));
            documents.post(positive.id());
            assertQuantity(batchId, "7");
            var zero = corrections.adjustment(adjust(batchId, "7", "7"));
            documents.post(zero.id());
            assertQuantity(batchId, "7");

            var stale = corrections.adjustment(adjust(batchId, "7", "6"));
            var intervening = corrections.disposal(exit(batchId, "1", null,
                    "New damage"));
            documents.post(intervening.id());
            assertEquals("STOCK_COUNT_STALE", assertThrows(BusinessException.class,
                    () -> documents.post(stale.id())).getCode());
            assertEquals("DRAFT", documents.get(stale.id()).status());
            assertQuantity(batchId, "6");
            assertEquals("INSUFFICIENT_STOCK", assertThrows(BusinessException.class,
                    () -> documents.post(corrections.supplierReturn(exit(batchId, "7", supplierId,
                            "Too much")).id())).getCode());
        } finally {
            SecurityContextHolder.clearContext();
            // Include failed draft created in the final assertion.
            jdbc.queryForList("SELECT DISTINCT d.id FROM stock_document d JOIN stock_document_item i "
                    + "ON i.document_id = d.id JOIN medicine_batch b ON b.id = i.batch_id "
                    + "WHERE b.batch_number = ?", Long.class, "B" + tag).forEach(id -> {
                jdbc.update("DELETE FROM stock_document_item WHERE document_id = ?", id);
                jdbc.update("DELETE FROM stock_document WHERE id = ?", id);
            });
            jdbc.update("DELETE FROM medicine_batch WHERE batch_number = ?", "B" + tag);
            jdbc.update("DELETE FROM medicine WHERE code = ?", "M" + tag);
            jdbc.update("DELETE FROM supplier WHERE code = ?", "S" + tag);
            accounts.deleteById(pharmacist.getId());
        }
    }

    private StockExitRequest exit(long batch, String quantity, Long supplierId,
            String reason) {
        return new StockExitRequest(OffsetDateTime.now(), "MAIN", "AVAILABLE",
                supplierId, reason, List.of(new StockExitLineRequest(batch,
                new BigDecimal(quantity), null)));
    }

    private AdjustmentRequest adjust(long batch, String book, String counted) {
        return new AdjustmentRequest(OffsetDateTime.now(), "MAIN", "AVAILABLE", "Counted stock",
                List.of(new AdjustmentLineRequest(batch, new BigDecimal(book),
                        new BigDecimal(counted), null)));
    }

    private void assertQuantity(long batchId, String expected) {
        BigDecimal actual = new TransactionTemplate(transactions).execute(status ->
                balances.balance(new StockPosition(batchId, "MAIN", "AVAILABLE")));
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
