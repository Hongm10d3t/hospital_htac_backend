package com.htac_backend.modules.procurement.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.pharmacy.dto.InboundStockLineRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineBatchWriteRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineWriteRequest;
import com.htac_backend.modules.pharmacy.dto.ReceiptStockRequest;
import com.htac_backend.modules.pharmacy.dto.SupplierWriteRequest;
import com.htac_backend.modules.pharmacy.service.InboundStockService;
import com.htac_backend.modules.pharmacy.service.MedicineBatchService;
import com.htac_backend.modules.pharmacy.service.MedicineCatalogService;
import com.htac_backend.modules.pharmacy.service.StockDocumentService;
import com.htac_backend.modules.pharmacy.service.StockBalanceService;
import com.htac_backend.modules.pharmacy.service.StockPosition;
import com.htac_backend.modules.pharmacy.service.SupplierCatalogService;
import com.htac_backend.modules.procurement.dto.PurchaseRequestLineWrite;
import com.htac_backend.modules.procurement.dto.PurchaseOrderRequest;
import com.htac_backend.modules.procurement.dto.PurchaseRequestWrite;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
import org.springframework.security.access.AccessDeniedException;
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
class PurchaseRequestDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired UserAccountRepository accounts;
    @Autowired MedicineCatalogService medicines;
    @Autowired MedicineBatchService batches;
    @Autowired SupplierCatalogService suppliers;
    @Autowired PurchaseRequestService requests;
    @Autowired InboundStockService inbound;
    @Autowired StockDocumentService documents;
    @Autowired StockBalanceService balances;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    void decisionsReceiptsCloseAndConcurrentPostingRespectApprovedQuantities() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 10);
        UserAccount pharmacist = accounts.saveAndFlush(staff("p" + tag, AccountRole.PHARMACIST));
        UserAccount colleague = accounts.saveAndFlush(staff("c" + tag, AccountRole.PHARMACIST));
        UserAccount admin = accounts.saveAndFlush(staff("a" + tag, AccountRole.ADMIN));
        List<Long> requestIds = new ArrayList<>();
        List<Long> documentIds = new ArrayList<>();
        try {
            authenticate(pharmacist);
            Long medicineId = medicines.create(medicine("M" + tag)).id();
            Long otherMedicineId = medicines.create(medicine("N" + tag)).id();
            Long batchId = batches.create(new MedicineBatchWriteRequest(medicineId, "B" + tag,
                    batches.businessDate().plusYears(1), "ACTIVE", null)).id();
            Long otherBatchId = batches.create(new MedicineBatchWriteRequest(otherMedicineId, "C" + tag,
                    batches.businessDate().plusYears(1), "ACTIVE", null)).id();
            Long supplierId = suppliers.create(new SupplierWriteRequest("S" + tag,
                    "Synthetic supplier", null, null, true)).id();
            Long otherSupplierId = suppliers.create(new SupplierWriteRequest("T" + tag,
                    "Synthetic supplier", null, null, true)).id();

            assertThrows(InvalidRequestException.class, () -> requests.create(new PurchaseRequestWrite(
                    supplierId, "Need", List.of(line(medicineId, "100"), line(medicineId, "1")))));
            assertThrows(InvalidRequestException.class, () -> requests.create(
                    write(supplierId, medicineId, "0")));
            var draft = requests.create(write(supplierId, medicineId, "100"));
            requestIds.add(draft.id());
            assertEquals(0, requests.update(draft.id(), write(supplierId, medicineId, "100"))
                    .items().getFirst().quantity().compareTo(new BigDecimal("100")));
            authenticate(colleague);
            assertThrows(AccessDeniedException.class, () -> requests.update(draft.id(),
                    write(supplierId, medicineId, "90")));
            authenticate(pharmacist);
            requests.submit(draft.id());
            assertEquals("PENDING", requests.submit(draft.id()).status());
            assertEquals("PURCHASE_NOT_RECEIVABLE", assertThrows(BusinessException.class,
                    () -> inbound.receipt(receipt(supplierId, draft.id(), batchId, "1")))
                    .getCode());
            authenticate(admin);
            var approved = requests.approve(draft.id(), "Restock");
            assertEquals(admin.getId(), approved.reviewedBy());
            assertEquals(approved.reviewedAt(), requests.approve(draft.id(), null).reviewedAt());
            assertEquals("PURCHASE_STATE_CONFLICT", assertThrows(BusinessException.class,
                    () -> requests.reject(draft.id(), "No")).getCode());
            authenticate(pharmacist);
            assertThrows(BusinessException.class, () -> requests.update(draft.id(),
                    write(supplierId, medicineId, "101")));
            var orderBody = new PurchaseOrderRequest(OffsetDateTime.now(),
                    batches.businessDate().plusDays(1));
            assertThrows(InvalidRequestException.class, () -> requests.order(draft.id(),
                    new PurchaseOrderRequest(OffsetDateTime.now(), batches.businessDate().minusDays(1))));
            assertEquals(orderBody.expectedDeliveryDate(), requests.order(draft.id(), orderBody)
                    .expectedDeliveryDate());
            assertEquals(orderBody.orderedAt().toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                    requests.order(draft.id(), orderBody).orderedAt());
            assertEquals("PURCHASE_SUPPLIER_MISMATCH", assertThrows(BusinessException.class,
                    () -> inbound.receipt(receipt(otherSupplierId, draft.id(), batchId, "1")))
                    .getCode());
            assertEquals("PURCHASE_MEDICINE_MISMATCH", assertThrows(BusinessException.class,
                    () -> inbound.receipt(receipt(supplierId, draft.id(), otherBatchId, "1")))
                    .getCode());

            var first = inbound.receipt(receipt(supplierId, draft.id(), batchId, "40"));
            documentIds.add(first.id());
            assertEquals(0, requests.get(draft.id()).items().getFirst().receivedQuantity().signum());
            documents.post(first.id());
            documents.post(first.id());
            var second = inbound.receipt(receipt(supplierId, draft.id(), batchId, "60"));
            documentIds.add(second.id());
            documents.post(second.id());
            assertEquals(0, requests.get(draft.id()).items().getFirst().remainingQuantity().signum());
            assertEquals("PURCHASE_QUANTITY_EXCEEDED", assertThrows(BusinessException.class,
                    () -> inbound.receipt(receipt(supplierId, draft.id(), batchId, "1")))
                    .getCode());
            requests.close(draft.id(), null);
            assertEquals(0, requests.get(draft.id()).items().getFirst().receivedQuantity()
                    .compareTo(new BigDecimal("100")));
            assertEquals("PURCHASE_NOT_RECEIVABLE", assertThrows(BusinessException.class,
                    () -> inbound.receipt(receipt(supplierId, draft.id(), batchId, "1")))
                    .getCode());

            var partial = requests.create(write(supplierId, medicineId, "10"));
            requestIds.add(partial.id());
            requests.submit(partial.id());
            authenticate(admin);
            requests.approve(partial.id(), null);
            authenticate(pharmacist);
            var pending = inbound.receipt(receipt(supplierId, partial.id(), batchId, "5"));
            documentIds.add(pending.id());
            assertEquals("PURCHASE_RECEIPT_DRAFT_EXISTS", assertThrows(BusinessException.class,
                    () -> requests.close(partial.id(), "No longer needed")).getCode());
            documents.cancel(pending.id(), "No longer needed");
            assertThrows(InvalidRequestException.class, () -> requests.close(partial.id(), null));
            requests.close(partial.id(), "No longer needed");

            var rejected = requests.create(write(supplierId, medicineId, "3"));
            requestIds.add(rejected.id());
            requests.submit(rejected.id());
            authenticate(admin);
            assertThrows(InvalidRequestException.class, () -> requests.reject(rejected.id(), null));
            var rejection = requests.reject(rejected.id(), "Budget changed");
            assertEquals("REJECTED", rejection.status());
            assertEquals(rejection.reviewedAt(), requests.reject(rejected.id(), "Different")
                    .reviewedAt());
            authenticate(pharmacist);

            var contested = requests.create(write(supplierId, medicineId, "4"));
            requestIds.add(contested.id());
            requests.submit(contested.id());
            var decisionStart = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var approve = pool.submit(() -> decideAfter(decisionStart, admin, contested.id(), true));
                var reject = pool.submit(() -> decideAfter(decisionStart, admin, contested.id(), false));
                decisionStart.countDown();
                assertEquals(1, (approve.get(20, TimeUnit.SECONDS) ? 1 : 0)
                        + (reject.get(20, TimeUnit.SECONDS) ? 1 : 0));
            }
            assertEquals(admin.getId(), requests.get(contested.id()).reviewedBy());

            var competing = requests.create(write(supplierId, medicineId, "10"));
            requestIds.add(competing.id());
            requests.submit(competing.id());
            authenticate(admin);
            requests.approve(competing.id(), null);
            authenticate(pharmacist);
            var a = inbound.receipt(receipt(supplierId, competing.id(), batchId, "6"));
            var b = inbound.receipt(receipt(supplierId, competing.id(), batchId, "6"));
            documentIds.add(a.id());
            documentIds.add(b.id());
            var start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var one = pool.submit(() -> postAfter(start, pharmacist, a.id()));
                var two = pool.submit(() -> postAfter(start, pharmacist, b.id()));
                start.countDown();
                int success = (one.get(20, TimeUnit.SECONDS) ? 1 : 0)
                        + (two.get(20, TimeUnit.SECONDS) ? 1 : 0);
                assertEquals(1, success);
            }
            assertEquals(0, requests.get(competing.id()).items().getFirst().receivedQuantity()
                    .compareTo(new BigDecimal("6")));
            assertEquals(0, jdbc.queryForObject("SELECT COALESCE(SUM(i.quantity),0) FROM "
                    + "stock_document_item i JOIN stock_document d ON d.id=i.document_id "
                    + "WHERE d.purchase_request_id=? AND d.status='POSTED'", BigDecimal.class,
                    competing.id()).compareTo(new BigDecimal("6")));
            assertEquals(0, new TransactionTemplate(transactionManager).execute(status ->
                    balances.balance(new StockPosition(batchId, "MAIN", "AVAILABLE")))
                    .compareTo(new BigDecimal("106")));
        } finally {
            SecurityContextHolder.clearContext();
            for (Long id : documentIds) {
                jdbc.update("DELETE FROM stock_document_item WHERE document_id=?", id);
                jdbc.update("DELETE FROM stock_document WHERE id=?", id);
            }
            for (Long id : requestIds) {
                jdbc.update("DELETE FROM purchase_request_item WHERE request_id=?", id);
                jdbc.update("DELETE FROM purchase_request WHERE id=?", id);
            }
            jdbc.update("DELETE FROM medicine_batch WHERE batch_number IN (?,?)", "B" + tag, "C" + tag);
            jdbc.update("DELETE FROM medicine WHERE code IN (?,?)", "M" + tag, "N" + tag);
            jdbc.update("DELETE FROM supplier WHERE code IN (?,?)", "S" + tag, "T" + tag);
            accounts.deleteById(pharmacist.getId());
            accounts.deleteById(colleague.getId());
            accounts.deleteById(admin.getId());
        }
    }

    private boolean postAfter(CountDownLatch start, UserAccount actor, Long id) {
        try {
            authenticate(actor);
            start.await();
            documents.post(id);
            return true;
        } catch (BusinessException exception) {
            assertEquals("PURCHASE_QUANTITY_EXCEEDED", exception.getCode());
            return false;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean decideAfter(CountDownLatch start, UserAccount actor, Long id, boolean approve) {
        try {
            authenticate(actor);
            start.await();
            if (approve) requests.approve(id, null);
            else requests.reject(id, "No budget");
            return true;
        } catch (BusinessException exception) {
            assertEquals("PURCHASE_STATE_CONFLICT", exception.getCode());
            return false;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UserAccount staff(String username, AccountRole role) {
        return new UserAccount(username, "invalid-test-hash", "Synthetic staff", "0000000000", role);
    }

    private MedicineWriteRequest medicine(String code) {
        return new MedicineWriteRequest(code, "Synthetic medicine", "Synthetic ingredient",
                "1 mg", "tablet", "tablet", BigDecimal.ZERO, 0, true);
    }

    private PurchaseRequestLineWrite line(Long medicineId, String quantity) {
        return new PurchaseRequestLineWrite(medicineId, new BigDecimal(quantity), null);
    }

    private PurchaseRequestWrite write(Long supplierId, Long medicineId, String quantity) {
        return new PurchaseRequestWrite(supplierId, "Need stock", List.of(line(medicineId, quantity)));
    }

    private ReceiptStockRequest receipt(Long supplierId, Long requestId, Long batchId, String quantity) {
        return new ReceiptStockRequest(OffsetDateTime.now(), "MAIN", "AVAILABLE", supplierId,
                requestId, null, List.of(new InboundStockLineRequest(batchId,
                        new BigDecimal(quantity), null, null)));
    }

    private void authenticate(UserAccount account) {
        var principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
