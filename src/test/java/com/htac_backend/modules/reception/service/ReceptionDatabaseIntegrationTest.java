package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.reception.dto.DirectReceptionRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.PageQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class ReceptionDatabaseIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired ReceptionService reception;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void bookedAndDirectShareCapacityAndWaitingList() {
        Fixture f = fixture(2);
        try {
            authenticate(f.receptionist());
            long appointmentId = appointment(f, f.patientA());
            var booked = reception.receiveBooked(appointmentId);
            var repeated = reception.receiveBooked(appointmentId);
            assertEquals(booked.id(), repeated.id());
            assertEquals(appointmentId, booked.appointmentId());
            assertEquals(f.patientA(), booked.patientId());
            assertEquals(f.roomId(), booked.roomId());
            var direct = reception.receiveDirect(new DirectReceptionRequest(f.patientB(), f.slotId(), "Walk in"));
            assertNull(direct.appointmentId());
            assertEquals(2, direct.queueNumber());
            assertEquals(2, reception.waiting(LocalDate.now(ZONE), f.roomId(), null,
                    PageQuery.parse(null, null, null, "queueNumber", Set.of("queueNumber"))).totalItems());
            assertEquals("SLOT_FULL", assertThrows(BusinessException.class,
                    () -> reception.receiveDirect(new DirectReceptionRequest(f.patientB(), f.slotId(), null)))
                    .getCode());
            assertEquals(2L, visitCount(f));
        } finally {
            SecurityContextHolder.clearContext();
            cleanup(f);
        }
    }

    @Test
    void twoDirectRequestsCannotExceedOnePlace() throws Exception {
        Fixture f = fixture(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        try {
            try (var pool = Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> receiveConcurrently(f, f.patientA(), ready, start, success, full));
                var b = pool.submit(() -> receiveConcurrently(f, f.patientB(), ready, start, success, full));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                a.get(15, TimeUnit.SECONDS);
                b.get(15, TimeUnit.SECONDS);
            }
            assertEquals(1, success.get());
            assertEquals(1, full.get());
            assertEquals(1L, visitCount(f));
        } finally {
            cleanup(f);
        }
    }

    @Test
    void bookedPlaceCannotBeTakenByConcurrentDirectReception() throws Exception {
        Fixture f = fixture(1);
        long appointmentId = appointment(f, f.patientA());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger booked = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        try {
            try (var pool = Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> {
                    authenticate(f.receptionist());
                    ready.countDown();
                    try {
                        start.await();
                        reception.receiveBooked(appointmentId);
                        booked.incrementAndGet();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                    return null;
                });
                var b = pool.submit(() -> receiveConcurrently(f, f.patientB(), ready, start,
                        new AtomicInteger(), full));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                a.get(15, TimeUnit.SECONDS);
                b.get(15, TimeUnit.SECONDS);
            }
            assertEquals(1, booked.get());
            assertEquals(1, full.get());
            assertEquals(1L, visitCount(f));
        } finally {
            cleanup(f);
        }
    }

    private void receiveConcurrently(Fixture f, Long patientId, CountDownLatch ready,
            CountDownLatch start, AtomicInteger success, AtomicInteger full) {
        authenticate(f.receptionist());
        ready.countDown();
        try {
            start.await();
            reception.receiveDirect(new DirectReceptionRequest(patientId, f.slotId(), null));
            success.incrementAndGet();
        } catch (BusinessException error) {
            if ("SLOT_FULL".equals(error.getCode())) {
                full.incrementAndGet();
            } else {
                throw error;
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Fixture fixture(int capacity) {
        String tag = UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO specialty (code, name) VALUES (?, ?)", "s" + tag, "Synthetic specialty");
        long specialtyId = id("SELECT id FROM specialty WHERE code = ?", "s" + tag);
        jdbc.update("INSERT INTO clinic_room (specialty_id, code, name) VALUES (?, ?, ?)",
                specialtyId, "r" + tag, "Synthetic room");
        long roomId = id("SELECT id FROM clinic_room WHERE code = ?", "r" + tag);
        UserAccount doctor = accounts.saveAndFlush(new UserAccount("d" + tag, "{noop}unused",
                "Synthetic doctor", "0000000000", AccountRole.DOCTOR));
        UserAccount receptionist = accounts.saveAndFlush(new UserAccount("e" + tag, "{noop}unused",
                "Synthetic receptionist", "0000000000", AccountRole.RECEPTIONIST));
        jdbc.update("INSERT INTO doctor_profile (user_id, specialty_id) VALUES (?, ?)",
                doctor.getId(), specialtyId);
        Instant start = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        jdbc.update("INSERT INTO clinic_slot (room_id, doctor_id, start_at, end_at, capacity) "
                + "VALUES (?, ?, ?, ?, ?)", roomId, doctor.getId(), Timestamp.from(start),
                Timestamp.from(start.plusSeconds(86400)), capacity);
        long slotId = id("SELECT id FROM clinic_slot WHERE room_id = ?", roomId);
        jdbc.update("INSERT INTO patient (patient_code, full_name, date_of_birth, gender) VALUES (?, ?, ?, ?)",
                "Pa" + tag, "Synthetic A", LocalDate.of(1990, 1, 1), "OTHER");
        jdbc.update("INSERT INTO patient (patient_code, full_name, date_of_birth, gender) VALUES (?, ?, ?, ?)",
                "Pb" + tag, "Synthetic B", LocalDate.of(1990, 1, 1), "OTHER");
        return new Fixture(tag, specialtyId, roomId, slotId, doctor, receptionist,
                id("SELECT id FROM patient WHERE patient_code = ?", "Pa" + tag),
                id("SELECT id FROM patient WHERE patient_code = ?", "Pb" + tag));
    }

    private long appointment(Fixture f, Long patientId) {
        jdbc.update("INSERT INTO appointment (appointment_code, patient_id, slot_id, reason) "
                + "VALUES (?, ?, ?, ?)", "A" + f.tag(), patientId, f.slotId(), "Booked checkup");
        return id("SELECT id FROM appointment WHERE appointment_code = ?", "A" + f.tag());
    }

    private long visitCount(Fixture f) {
        return id("SELECT COUNT(*) FROM medical_visit WHERE slot_id = ?", f.slotId());
    }

    private long id(String query, Object parameter) {
        return jdbc.queryForObject(query, Long.class, parameter);
    }

    private void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private void cleanup(Fixture f) {
        jdbc.update("DELETE FROM medical_visit WHERE slot_id = ?", f.slotId());
        jdbc.update("DELETE FROM appointment WHERE slot_id = ?", f.slotId());
        jdbc.update("DELETE FROM patient WHERE id IN (?, ?)", f.patientA(), f.patientB());
        jdbc.update("DELETE FROM clinic_slot WHERE id = ?", f.slotId());
        jdbc.update("DELETE FROM doctor_profile WHERE user_id = ?", f.doctor().getId());
        jdbc.update("DELETE FROM clinic_room WHERE id = ?", f.roomId());
        jdbc.update("DELETE FROM specialty WHERE id = ?", f.specialtyId());
        accounts.deleteById(f.doctor().getId());
        accounts.deleteById(f.receptionist().getId());
    }

    private record Fixture(String tag, Long specialtyId, Long roomId, Long slotId, UserAccount doctor,
            UserAccount receptionist, Long patientA, Long patientB) { }
}
