package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.pharmacy.dto.DispenseLineRequest;
import com.htac_backend.modules.pharmacy.dto.DispenseStockRequest;
import com.htac_backend.modules.pharmacy.dto.InboundStockLineRequest;
import com.htac_backend.modules.pharmacy.dto.OpeningStockRequest;
import com.htac_backend.modules.pharmacy.dto.ReviewUnfilledRequest;
import com.htac_backend.modules.pharmacy.dto.UnfilledLineRequest;
import com.htac_backend.shared.api.InvalidRequestException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class UnfilledReviewDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired InboundStockService inbound;
    @Autowired DispenseStockService dispensing;
    @Autowired UnfilledReviewService reviews;
    @Autowired StockDocumentService documents;
    @Autowired MedicineBatchService batches;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void reviewReplacesCurrentQuantityAndDistinguishesUntouchedZero() {
        try (var f = new PharmacyPrescriptionTestFixture(jdbc, accounts)) {
            long medicine = f.medicine("1");
            var today = batches.businessDate();
            long batch = f.batch(medicine, "1", today.plusYears(1));
            long prescription = f.prescription(medicine, new BigDecimal("10"), "ISSUED");
            long item = f.prescriptionItem(prescription);
            long untouched = f.prescription(medicine, BigDecimal.ONE, "ISSUED");
            long untouchedItem = f.prescriptionItem(untouched);
            f.authenticate(f.pharmacist);
            assertEquals(false, dispensing.snapshot(untouched).reviewed());
            assertEquals(0, dispensing.snapshot(untouched).items().getFirst()
                    .unfilledQuantity().compareTo(BigDecimal.ZERO));
            var reviewedZero = reviews.review(untouched, request(untouchedItem, "0", null, null));
            assertEquals(true, reviewedZero.reviewed());
            assertNotNull(reviewedZero.dispensingCheckedAt());

            documents.post(inbound.opening(new OpeningStockRequest(today, "COUNTER", "AVAILABLE",
                    null, List.of(new InboundStockLineRequest(batch, new BigDecimal("10"),
                            null, null)))).id());
            documents.post(dispensing.create(new DispenseStockRequest(prescription,
                    OffsetDateTime.now(), List.of(new DispenseLineRequest(item, batch,
                    new BigDecimal("6"))))).id());
            assertEquals(false, dispensing.snapshot(prescription).reviewed());
            assertThrows(InvalidRequestException.class, () -> reviews.review(prescription,
                    request(item, "5", "STOCKOUT", null)));
            assertThrows(InvalidRequestException.class, () -> reviews.review(prescription,
                    request(item, "-1", "STOCKOUT", null)));
            assertThrows(InvalidRequestException.class, () -> reviews.review(prescription,
                    request(item, "4", "OTHER", null)));
            var first = reviews.review(prescription, request(item, "4", "STOCKOUT", null));
            assertEquals(0, first.items().getFirst().unfilledQuantity()
                    .compareTo(new BigDecimal("4")));
            var second = reviews.review(prescription, request(item, "2", "OTHER", "Outside purchase"));
            assertEquals(0, second.items().getFirst().unfilledQuantity()
                    .compareTo(new BigDecimal("2")));
            assertEquals("OTHER", second.items().getFirst().unfilledReason());
            reviews.review(prescription, request(item, "4", "STOCKOUT", null));
            documents.post(dispensing.create(new DispenseStockRequest(prescription,
                    OffsetDateTime.now(), List.of(new DispenseLineRequest(item, batch,
                    new BigDecimal("2"))))).id());
            var finalState = dispensing.snapshot(prescription);
            assertEquals(0, finalState.items().getFirst().unfilledQuantity()
                    .compareTo(new BigDecimal("2")));
            assertEquals(0, finalState.items().getFirst().remainingQuantity()
                    .compareTo(new BigDecimal("2")));
            assertNotNull(finalState.dispensingCheckedAt());
        }
    }

    private ReviewUnfilledRequest request(long itemId, String quantity, String reason, String note) {
        return new ReviewUnfilledRequest(List.of(new UnfilledLineRequest(itemId,
                new BigDecimal(quantity), reason, note)));
    }
}
