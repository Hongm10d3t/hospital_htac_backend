package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class DoctorVisitDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired DoctorVisitService doctorVisits;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void assignedDoctorSeesWorkAndHistoryAndStartIsIdempotent() {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            f.authenticate(f.doctorA);
            var day = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            assertEquals(1, doctorVisits.slots(day, page("startAt")).totalItems());
            assertEquals(f.visitA, doctorVisits.waiting(f.slotA, page("queueNumber"))
                    .items().getFirst().id());
            assertEquals(2, doctorVisits.patientHistory(f.visitA, page("arrivedAt")).totalItems());
            assertEquals("Historical diagnosis", doctorVisits.patientHistoryDetail(f.visitA,
                    f.historicVisit).visit().diagnosis());
            assertThrows(NotFoundException.class, () -> doctorVisits.patientHistoryDetail(f.visitA, f.visitB));

            var started = doctorVisits.start(f.visitA);
            assertEquals("IN_PROGRESS", started.status().name());
            assertNotNull(started.startedAt());
            assertEquals(started.startedAt(), doctorVisits.start(f.visitA).startedAt());
            assertEquals(0, doctorVisits.waiting(f.slotA, page("queueNumber")).totalItems());
            f.authenticate(f.doctorB);
            assertThrows(NotFoundException.class, () -> doctorVisits.get(f.visitA));
            assertThrows(NotFoundException.class, () -> doctorVisits.start(f.visitA));
            assertThrows(NotFoundException.class, () -> doctorVisits.waiting(f.slotA, page("queueNumber")));
            assertThrows(NotFoundException.class, () -> doctorVisits.patientHistory(f.visitA,
                    page("arrivedAt")));
        }
    }

    @Test
    void concurrentStartKeepsFirstTimestamp() throws Exception {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> startVisit(f, ready, start));
                var b = pool.submit(() -> startVisit(f, ready, start));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                Instant first = a.get(15, TimeUnit.SECONDS);
                Instant second = b.get(15, TimeUnit.SECONDS);
                assertEquals(first, second);
                assertEquals(first, jdbc.queryForObject("SELECT started_at FROM medical_visit WHERE id = ?",
                        java.sql.Timestamp.class, f.visitA).toInstant());
            }
        }
    }

    private Instant startVisit(OutpatientTestFixture f, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        f.authenticate(f.doctorA);
        ready.countDown();
        try {
            start.await();
            return doctorVisits.start(f.visitA).startedAt();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private PageQuery page(String field) {
        return PageQuery.parse(0, 20, field + ",ASC", field, Set.of(field));
    }
}
