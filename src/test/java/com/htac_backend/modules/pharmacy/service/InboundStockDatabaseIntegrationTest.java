package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.pharmacy.dto.InboundStockLineRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineBatchWriteRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineWriteRequest;
import com.htac_backend.modules.pharmacy.dto.OpeningStockRequest;
import com.htac_backend.modules.pharmacy.dto.ReceiptStockRequest;
import com.htac_backend.modules.pharmacy.dto.SupplierWriteRequest;
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
class InboundStockDatabaseIntegrationTest {
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
    @Autowired StockDocumentService documents;
    @Autowired StockBalanceService balances;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void openingAndReceiptChangeBalanceOnlyAfterPosting() {
        String tag = UUID.randomUUID().toString().substring(0, 12);
        UserAccount pharmacist = accounts.saveAndFlush(new UserAccount("i" + tag,
                "{noop}unused", "Synthetic staff", "0000000000", AccountRole.PHARMACIST));
        authenticate(pharmacist);
        List<Long> documentIds = new ArrayList<>();
        try {
            Long medicineId = medicines.create(new MedicineWriteRequest("M" + tag,
                    "Synthetic medicine", "Synthetic ingredient", "1 mg", "tablet", "tablet",
                    BigDecimal.ZERO, 0, true)).id();
            Long batchId = batches.create(new MedicineBatchWriteRequest(medicineId, "B" + tag,
                    batches.businessDate().plusYears(1), "ACTIVE", null)).id();
            Long supplierId = suppliers.create(new SupplierWriteRequest("S" + tag,
                    "Synthetic supplier", null, null, true)).id();
            var opening = inbound.opening(new OpeningStockRequest(batches.businessDate(),
                    "MAIN", "AVAILABLE", "Initial count", List.of(line(batchId, "10", null))));
            documentIds.add(opening.id());
            assertEquals("DRAFT", opening.status());
            assertEquals(0, balance(batchId).compareTo(BigDecimal.ZERO));
            documents.post(opening.id());
            assertEquals(0, balance(batchId).compareTo(new BigDecimal("10")));
            assertEquals("STOCK_DOCUMENT_FINAL", assertThrows(BusinessException.class,
                    () -> documents.cancel(opening.id(), "Wrong count")).getCode());

            assertThrows(InvalidRequestException.class, () -> inbound.receipt(
                    new ReceiptStockRequest(OffsetDateTime.now(), "MAIN", "AVAILABLE", null,
                            null, List.of(line(batchId, "2", null)))));
            assertThrows(InvalidRequestException.class, () -> inbound.opening(
                    new OpeningStockRequest(batches.businessDate(), "MAIN", "AVAILABLE",
                            null, List.of(line(batchId, "0", null)))));
            var receipt = inbound.receipt(new ReceiptStockRequest(OffsetDateTime.now(),
                    "MAIN", "AVAILABLE", supplierId, null,
                    List.of(line(batchId, "2", new BigDecimal("1.25")))));
            documentIds.add(receipt.id());
            assertEquals(0, balance(batchId).compareTo(new BigDecimal("10")));
            suppliers.update(supplierId, new SupplierWriteRequest("S" + tag,
                    "Synthetic supplier", null, null, false));
            assertEquals("SUPPLIER_INACTIVE", assertThrows(BusinessException.class,
                    () -> documents.post(receipt.id())).getCode());
            suppliers.update(supplierId, new SupplierWriteRequest("S" + tag,
                    "Synthetic supplier", null, null, true));
            documents.post(receipt.id());
            documents.post(receipt.id());
            assertEquals(0, balance(batchId).compareTo(new BigDecimal("12")));
            var quarantine = inbound.receipt(new ReceiptStockRequest(OffsetDateTime.now(),
                    "COUNTER", "QUARANTINE", supplierId, "Damaged packaging",
                    List.of(line(batchId, "3", null))));
            documentIds.add(quarantine.id());
            documents.post(quarantine.id());
            assertEquals(0, position(batchId, "COUNTER", "QUARANTINE")
                    .compareTo(new BigDecimal("3")));
            assertEquals(0, balance(batchId).compareTo(new BigDecimal("12")));
        } finally {
            SecurityContextHolder.clearContext();
            for (Long id : documentIds) {
                jdbc.update("DELETE FROM stock_document_item WHERE document_id = ?", id);
                jdbc.update("DELETE FROM stock_document WHERE id = ?", id);
            }
            jdbc.update("DELETE FROM medicine_batch WHERE batch_number = ?", "B" + tag);
            jdbc.update("DELETE FROM medicine WHERE code = ?", "M" + tag);
            jdbc.update("DELETE FROM supplier WHERE code = ?", "S" + tag);
            accounts.deleteById(pharmacist.getId());
        }
    }

    private InboundStockLineRequest line(Long batchId, String quantity, BigDecimal cost) {
        return new InboundStockLineRequest(batchId, new BigDecimal(quantity), cost, null);
    }

    private BigDecimal balance(Long batchId) {
        return position(batchId, "MAIN", "AVAILABLE");
    }

    private BigDecimal position(Long batchId, String location, String bucket) {
        return new TransactionTemplate(transactionManager).execute(status ->
                balances.balance(new StockPosition(batchId, location, bucket)));
    }

    private void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
