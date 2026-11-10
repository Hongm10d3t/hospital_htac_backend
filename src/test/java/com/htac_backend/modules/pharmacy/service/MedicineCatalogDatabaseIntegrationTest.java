package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.pharmacy.dto.MedicineWriteRequest;
import com.htac_backend.modules.pharmacy.dto.SupplierWriteRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.PageQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
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
class MedicineCatalogDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired MedicineCatalogService medicines;
    @Autowired SupplierCatalogService suppliers;
    @Autowired MedicineSelectionService selection;
    @Autowired MedicineDisplayLookupService display;
    @Autowired PlatformTransactionManager transactions;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void medicineIdentityFreezesAfterReferenceAndInactiveLabelStaysReadable() {
        String tag = UUID.randomUUID().toString().substring(0, 12);
        UserAccount pharmacist = account("f" + tag, AccountRole.PHARMACIST);
        UserAccount doctor = account("d" + tag, AccountRole.DOCTOR);
        Long firstId = null;
        Long secondId = null;
        try {
            authenticate(pharmacist);
            var first = medicines.create(request("M" + tag, true, "tablet", BigDecimal.ZERO, 5));
            firstId = first.id();
            assertEquals("MEDICINE_CODE_TAKEN", assertThrows(BusinessException.class,
                    () -> medicines.create(request("M" + tag, true, "tablet", BigDecimal.ZERO, 5)))
                    .getCode());
            assertThrows(InvalidRequestException.class, () -> medicines.create(
                    request("X" + tag, true, "tablet", BigDecimal.ZERO, -1)));
            assertThrows(InvalidRequestException.class, () -> medicines.create(
                    request("Y" + tag, true, "tablet", new BigDecimal("-0.001"), 1)));
            assertEquals("capsule", medicines.update(first.id(), request("M" + tag, true,
                    "capsule", BigDecimal.ONE, 5)).dosageForm());
            jdbc.update("INSERT INTO medicine_batch (medicine_id, batch_number, expiry_date) "
                    + "VALUES (?, ?, ?)", first.id(), "B" + tag, LocalDate.now().plusYears(1));
            assertEquals("MEDICINE_IDENTITY_LOCKED", assertThrows(BusinessException.class,
                    () -> medicines.update(first.id(), request("M" + tag, true,
                            "tablet", BigDecimal.ONE, 5))).getCode());
            assertEquals(false, medicines.update(first.id(), request("M" + tag, false,
                    "capsule", new BigDecimal("2.000"), 8)).active());
            var tx = new TransactionTemplate(transactions);
            assertEquals("Synthetic medicine", tx.execute(status -> display.labels(
                    java.util.List.of(first.id())).get(first.id()).name()));
            var second = medicines.create(request("N" + tag, true, "tablet", BigDecimal.ZERO, 2));
            secondId = second.id();
            authenticate(doctor);
            assertEquals(1, selection.search(null, page()).totalItems());
            assertEquals(second.id(), selection.search(null, page()).items().getFirst().id());
            authenticate(pharmacist);
            assertEquals(2, medicines.search(null, null, page()).totalItems());
            assertEquals(1, medicines.search(null, false, page()).totalItems());
        } finally {
            SecurityContextHolder.clearContext();
            jdbc.update("DELETE FROM medicine_batch WHERE batch_number = ?", "B" + tag);
            jdbc.update("DELETE FROM medicine WHERE code IN (?, ?)", "M" + tag, "N" + tag);
            accounts.deleteById(pharmacist.getId());
            accounts.deleteById(doctor.getId());
        }
    }

    @Test
    void supplierCodeAndAdministrativeFieldsAreValidated() {
        String tag = UUID.randomUUID().toString().substring(0, 12);
        UserAccount pharmacist = account("f" + tag, AccountRole.PHARMACIST);
        Long supplierId = null;
        try {
            authenticate(pharmacist);
            var created = suppliers.create(new SupplierWriteRequest("S" + tag,
                    "Synthetic supplier", null, null, true));
            supplierId = created.id();
            assertEquals("SUPPLIER_CODE_TAKEN", assertThrows(BusinessException.class,
                    () -> suppliers.create(new SupplierWriteRequest("S" + tag,
                            "Another", null, null, true))).getCode());
            assertEquals(false, suppliers.update(created.id(), new SupplierWriteRequest(
                    "S" + tag, "Synthetic supplier", "0000000000", null, false)).active());
            assertEquals(1, suppliers.search(null, false, page()).totalItems());
            assertEquals("0000000000", suppliers.get(created.id()).phone());
        } finally {
            SecurityContextHolder.clearContext();
            jdbc.update("DELETE FROM supplier WHERE code = ?", "S" + tag);
            accounts.deleteById(pharmacist.getId());
        }
    }

    private MedicineWriteRequest request(String code, boolean active, String form,
            BigDecimal minimum, int leadTime) {
        return new MedicineWriteRequest(code, "Synthetic medicine", "Synthetic ingredient",
                "1 mg", form, "tablet", minimum, leadTime, active);
    }

    private PageQuery page() {
        return PageQuery.parse(0, 20, "id,ASC", "id", Set.of("id"));
    }

    private UserAccount account(String username, AccountRole role) {
        return accounts.saveAndFlush(new UserAccount(username, "{noop}unused",
                "Synthetic staff", "0000000000", role));
    }

    private void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
