package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.dto.UpsertHospitalConfigRequest;
import com.htac_backend.shared.api.InvalidRequestException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class HospitalConfigDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired HospitalConfigService configs;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void createAndUpdateKeepOneRecordAndExposeCurrentPolicy() {
        Assumptions.assumeTrue(count() == 0L);
        var created = configs.upsert(request(7));
        assertTrue(created.created());
        assertEquals(1L, count());
        assertEquals(7, configs.maxAdvanceBookingDays());
        assertEquals(7, configs.publicInfo().advanceBookingDays());
        LocalDate hospitalDate = LocalDate.of(2026, 11, 9);
        assertTrue(configs.isWithinAdvanceWindow(hospitalDate.plusDays(7), hospitalDate));
        assertFalse(configs.isWithinAdvanceWindow(hospitalDate.plusDays(8), hospitalDate));

        var updated = configs.upsert(request(14));
        assertFalse(updated.created());
        assertEquals(1L, count());
        assertEquals(14, configs.maxAdvanceBookingDays());
        assertEquals(14, configs.publicInfo().advanceBookingDays());
        assertTrue(configs.isWithinAdvanceWindow(hospitalDate.plusDays(8), hospitalDate));
        assertThrows(InvalidRequestException.class, () -> configs.upsert(request(0)));
        assertThrows(InvalidRequestException.class, () -> configs.upsert(
                new UpsertHospitalConfigRequest("Hospital", "Address", "0000000000", null,
                        "http://invalid.example/logo.png", null, 7)));
    }

    @Test
    void simultaneousFirstWritesCannotCreateTwoConfigs() throws Exception {
        Assumptions.assumeTrue(count() == 0L);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicReference<Exception> rejected = new AtomicReference<>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> attempt(7, ready, start, succeeded, rejected));
            var second = pool.submit(() -> attempt(14, ready, start, succeeded, rejected));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertTrue(succeeded.get() >= 1);
            assertEquals(1L, count());
            if (rejected.get() != null) {
                assertTrue(rejected.get() instanceof DataIntegrityViolationException
                                || rejected.get() instanceof PessimisticLockingFailureException,
                        () -> "Unexpected rejection type: " + rejected.get().getClass().getSimpleName());
            }
        } finally {
            jdbc.update("DELETE FROM hospital_config WHERE id = 1");
        }
    }

    private void attempt(int days, CountDownLatch ready, CountDownLatch start, AtomicInteger succeeded,
            AtomicReference<Exception> rejected) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        ready.countDown();
        try {
            start.await();
            configs.upsert(request(days));
            succeeded.incrementAndGet();
        } catch (Exception failure) {
            rejected.compareAndSet(null, failure);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM hospital_config", Long.class);
    }

    private UpsertHospitalConfigRequest request(int days) {
        return new UpsertHospitalConfigRequest("Synthetic Hospital", "Synthetic Address", "0000000000",
                null, "https://example.invalid/logo.png", null, days);
    }
}
