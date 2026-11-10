package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.pharmacy.dto.AdjustmentLineRequest;
import com.htac_backend.modules.pharmacy.dto.AdjustmentRequest;
import com.htac_backend.modules.pharmacy.dto.DispenseLineRequest;
import com.htac_backend.modules.pharmacy.dto.DispenseStockRequest;
import com.htac_backend.modules.pharmacy.dto.InboundStockLineRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineBatchWriteRequest;
import com.htac_backend.modules.pharmacy.dto.MedicineWriteRequest;
import com.htac_backend.modules.pharmacy.dto.OpeningStockRequest;
import com.htac_backend.modules.pharmacy.dto.PatientReturnLineRequest;
import com.htac_backend.modules.pharmacy.dto.PatientReturnRequest;
import com.htac_backend.modules.pharmacy.dto.ReceiptStockRequest;
import com.htac_backend.modules.pharmacy.dto.StockExitLineRequest;
import com.htac_backend.modules.pharmacy.dto.StockExitRequest;
import com.htac_backend.modules.pharmacy.dto.SupplierWriteRequest;
import com.htac_backend.modules.pharmacy.dto.TransferStockRequest;
import com.htac_backend.shared.api.PageQuery;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class InventoryDatabaseIntegrationTest {
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
    @Autowired TransferStockService transfers;
    @Autowired DispenseStockService dispensing;
    @Autowired PatientReturnService patientReturns;
    @Autowired StockCorrectionService corrections;
    @Autowired StockDocumentService documents;
    @Autowired InventoryQueryService inventory;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void everyMovementContributesToDetailSummaryAndAlerts() {
        var f = new PharmacyPrescriptionTestFixture(jdbc, accounts);
        String supplierCode = "S" + f.tag;
        try {
            long medicine = f.medicine("1");
            f.authenticate(f.pharmacist);
            medicines.update(medicine, new MedicineWriteRequest("m1" + f.tag,
                    "Synthetic medicine 1", "Synthetic ingredient", "1 mg", "tablet", "tablet",
                    new BigDecimal("11"), 0, true));
            var today = batches.businessDate();
            long near = f.batch(medicine, "1", today.plusDays(5));
            long normal = f.batch(medicine, "2", today.plusYears(1));
            long blocked = f.batch(medicine, "3", today.plusYears(1));
            long expired = f.batch(medicine, "4", today);
            long prescription = f.prescription(medicine, new BigDecimal("10"), "ISSUED");
            long prescriptionItem = f.prescriptionItem(prescription);
            long supplier = suppliers.create(new SupplierWriteRequest(supplierCode,
                    "Synthetic supplier", null, null, true)).id();
            var opening = inbound.opening(new OpeningStockRequest(today, "MAIN", "AVAILABLE",
                    null, List.of(line(near, "10"), line(normal, "5"), line(blocked, "3"),
                            line(expired, "2"))));
            documents.post(opening.id());
            documents.post(inbound.receipt(new ReceiptStockRequest(OffsetDateTime.now(),
                    "MAIN", "AVAILABLE", supplier, null, List.of(line(near, "2")))).id());
            documents.post(transfers.create(new TransferStockRequest(OffsetDateTime.now(),
                    "MAIN", "AVAILABLE", "COUNTER", "AVAILABLE", null, false,
                    List.of(line(near, "6")))).id());
            var dispense = dispensing.create(new DispenseStockRequest(prescription,
                    OffsetDateTime.now(), List.of(new DispenseLineRequest(prescriptionItem,
                    near, new BigDecimal("4")))));
            documents.post(dispense.id());
            documents.post(patientReturns.create(new PatientReturnRequest(prescription,
                    OffsetDateTime.now(), "Patient returned medicine",
                    List.of(new PatientReturnLineRequest(dispense.items().getFirst().id(),
                            prescriptionItem, near, BigDecimal.ONE, "SEALED", null)))).id());
            documents.post(corrections.supplierReturn(new StockExitRequest(OffsetDateTime.now(),
                    "MAIN", "AVAILABLE", supplier, "Return damaged packs",
                    List.of(new StockExitLineRequest(normal, new BigDecimal("2"), null)))).id());
            documents.post(corrections.disposal(new StockExitRequest(OffsetDateTime.now(),
                    "MAIN", "AVAILABLE", null, "Dispose damaged packs",
                    List.of(new StockExitLineRequest(blocked, BigDecimal.ONE, null)))).id());
            documents.post(corrections.adjustment(new AdjustmentRequest(OffsetDateTime.now(),
                    "MAIN", "AVAILABLE", "Counted stock",
                    List.of(new AdjustmentLineRequest(near, new BigDecimal("6"),
                            new BigDecimal("5"), null)))).id());
            batches.update(blocked, new MedicineBatchWriteRequest(medicine, "b3" + f.tag,
                    today.plusYears(1), "BLOCKED", "Safety review"));

            var result = inventory.medicine(medicine);
            assertEquals(0, result.physicalQuantity().compareTo(new BigDecimal("15")));
            assertEquals(0, result.dispensableQuantity().compareTo(new BigDecimal("10")));
            assertTrue(result.belowMinimum());
            assertEquals(30, result.expiryWarningDays());
            assertEquals(2, result.expiryAlerts().size());
            assertTrue(result.expiryAlerts().stream().anyMatch(alert ->
                    alert.batchId().equals(near) && alert.daysUntilExpiry() == 5));
            assertTrue(result.expiryAlerts().stream().anyMatch(alert ->
                    alert.batchId().equals(expired) && alert.expired()));
            assertEquals(0, result.positions().stream().map(position -> position.physicalQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(result.physicalQuantity()));
            assertEquals(0, result.positions().stream().map(position -> position.dispensableQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(result.dispensableQuantity()));
            assertEquals(1, inventory.search("m1" + f.tag, true,
                    PageQuery.parse(0, 20, "code,ASC", "code", Set.of("code"))).totalItems());

            var movements = inventory.movements(near, null, null, page());
            assertEquals(7, movements.totalItems());
            assertEquals(0, movements.items().stream().map(row -> row.deltaQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal("8")));
            assertEquals(4, inventory.movements(near, "MAIN", "AVAILABLE", page()).totalItems());
            medicines.update(medicine, new MedicineWriteRequest("m1" + f.tag,
                    "Synthetic medicine 1", "Synthetic ingredient", "1 mg", "tablet", "tablet",
                    new BigDecimal("11"), 0, false));
            assertEquals(0, inventory.medicine(medicine).dispensableQuantity()
                    .compareTo(BigDecimal.ZERO));
        } finally {
            jdbc.update("UPDATE supplier SET created_by = NULL, updated_by = NULL WHERE code = ?",
                    supplierCode);
            f.close();
            jdbc.update("DELETE FROM supplier WHERE code = ?", supplierCode);
        }
    }

    private InboundStockLineRequest line(long batch, String quantity) {
        return new InboundStockLineRequest(batch, new BigDecimal(quantity), null, null);
    }

    private PageQuery page() {
        return PageQuery.parse(0, 100, "occurredAt,DESC", "occurredAt",
                Set.of("occurredAt", "documentId", "itemId"));
    }
}
