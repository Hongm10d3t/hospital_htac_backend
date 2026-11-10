package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.pharmacy.dto.MedicineBatchWriteRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineWriteRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class MedicineBatchDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired MedicineBatchService batches;
    @Autowired MedicineCatalogService medicines;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void duplicateBlockAndExpiryBoundariesAreEnforced() {
        String tag = UUID.randomUUID().toString().substring(0, 12);
        UserAccount pharmacist = accounts.saveAndFlush(new UserAccount("b" + tag,
                "{noop}unused", "Synthetic staff", "0000000000", AccountRole.PHARMACIST));
        authenticate(pharmacist);
        Long medicineId = null;
        Long batchId = null;
        try {
            medicineId = medicines.create(new MedicineWriteRequest("M" + tag, "Synthetic medicine",
                    "Synthetic ingredient", "1 mg", "tablet", "tablet", BigDecimal.ZERO, 0, true)).id();
            LocalDate today = batches.businessDate();
            var request = new MedicineBatchWriteRequest(medicineId, "B" + tag,
                    today.plusDays(1), "ACTIVE", null);
            batchId = batches.create(request).id();
            final Long finalMedicineId = medicineId;
            final Long finalBatchId = batchId;
            assertEquals("BATCH_NUMBER_TAKEN", assertThrows(BusinessException.class,
                    () -> batches.create(request)).getCode());
            assertThrows(InvalidRequestException.class, () -> batches.create(
                    new MedicineBatchWriteRequest(finalMedicineId, "Other", today,
                            "BLOCKED", null)));
            var tx = new TransactionTemplate(transactionManager);
            assertEquals(true, tx.execute(status -> batches.isDispensable(
                    batches.requireDispensable(finalBatchId, today), today)));
            assertEquals(false, tx.execute(status -> batches.isDispensable(
                    batches.requireDispensable(finalBatchId, today), today.plusDays(1))));
            assertEquals(today, batches.update(batchId, new MedicineBatchWriteRequest(
                    medicineId, "B" + tag, today, "BLOCKED", "Damaged seal")).expiryDate());
            assertEquals("BATCH_NOT_DISPENSABLE", assertThrows(BusinessException.class,
                    () -> tx.execute(status -> batches.requireDispensable(finalBatchId, today)))
                    .getCode());
            batches.update(batchId, new MedicineBatchWriteRequest(medicineId, "B" + tag,
                    today, "ACTIVE", null));
            assertEquals("BATCH_NOT_DISPENSABLE", assertThrows(BusinessException.class,
                    () -> tx.execute(status -> batches.requireDispensable(finalBatchId, today)))
                    .getCode());
        } finally {
            SecurityContextHolder.clearContext();
            jdbc.update("DELETE FROM medicine_batch WHERE batch_number = ?", "B" + tag);
            jdbc.update("DELETE FROM medicine WHERE code = ?", "M" + tag);
            accounts.deleteById(pharmacist.getId());
        }
    }

    private void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
