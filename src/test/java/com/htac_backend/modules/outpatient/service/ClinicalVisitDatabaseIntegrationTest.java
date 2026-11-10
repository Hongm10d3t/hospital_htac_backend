package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.outpatient.dto.UpdateClinicalVisitRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.modules.scheduling.service.ReceptionAppointmentService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class ClinicalVisitDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired ClinicalVisitService clinical;
    @Autowired DoctorVisitService doctorVisits;
    @Autowired ReceptionAppointmentService receptionAppointments;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void diagnosisRequiredAndCompletionUpdatesAppointmentWithoutNewBooking() {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            f.authenticate(f.doctorA);
            doctorVisits.start(f.visitA);
            assertEquals("DIAGNOSIS_REQUIRED", assertThrows(BusinessException.class,
                    () -> clinical.complete(f.visitA)).getCode());
            assertThrows(InvalidRequestException.class, () -> clinical.update(f.visitA,
                    new UpdateClinicalVisitRequest(null, null, "Diagnosis", null,
                            LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1))));
            LocalDate followUp = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(7);
            clinical.update(f.visitA, new UpdateClinicalVisitRequest("Synthetic notes", "DX1",
                    "Synthetic diagnosis", "Synthetic plan", followUp));
            var completed = clinical.complete(f.visitA);
            assertEquals("COMPLETED", completed.status().name());
            assertEquals(followUp, completed.followUpDate());
            assertNotNull(completed.completedAt());
            assertEquals(completed.completedAt(), clinical.complete(f.visitA).completedAt());
            assertEquals("COMPLETED", jdbc.queryForObject(
                    "SELECT status FROM appointment WHERE id = ?", String.class, f.appointmentA));
            assertEquals(1L, f.id("SELECT COUNT(*) FROM appointment WHERE patient_id = ?", f.patientA));
            f.authenticate(f.doctorB);
            assertThrows(NotFoundException.class, () -> clinical.update(f.visitA,
                    new UpdateClinicalVisitRequest(null, null, "Other", null, null)));
        }
    }

    @Test
    void abandoningVisitNeedsReasonAndClosesAppointment() {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            f.authenticate(f.doctorA);
            doctorVisits.start(f.visitA);
            assertThrows(InvalidRequestException.class, () -> clinical.notComplete(f.visitA, " "));
            var ended = clinical.notComplete(f.visitA, "Patient left");
            assertEquals("NOT_COMPLETED", ended.status().name());
            assertNull(ended.completedAt());
            assertEquals("NOT_COMPLETED", jdbc.queryForObject(
                    "SELECT status FROM appointment WHERE id = ?", String.class, f.appointmentA));
            assertEquals("Patient left", clinical.notComplete(f.visitA, "Patient left").outcomeReason());
            assertEquals("VISIT_NOT_IN_PROGRESS", assertThrows(BusinessException.class,
                    () -> clinical.complete(f.visitA)).getCode());
        }
    }

    @Test
    void appointmentFailureRollsBackVisitCompletion() {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            f.authenticate(f.doctorA);
            doctorVisits.start(f.visitA);
            clinical.update(f.visitA, new UpdateClinicalVisitRequest(null, null,
                    "Synthetic diagnosis", null, null));
            jdbc.update("UPDATE appointment SET status = 'NOT_COMPLETED', "
                    + "non_completion_reason = 'Prior final outcome' WHERE id = ?", f.appointmentA);
            assertEquals("APPOINTMENT_FINAL", assertThrows(BusinessException.class,
                    () -> clinical.complete(f.visitA)).getCode());
            assertEquals("IN_PROGRESS", jdbc.queryForObject(
                    "SELECT status FROM medical_visit WHERE id = ?", String.class, f.visitA));
            assertNull(jdbc.queryForObject("SELECT completed_at FROM medical_visit WHERE id = ?",
                    java.sql.Timestamp.class, f.visitA));
        }
    }

    @Test
    void concurrentFinalOutcomesLeaveVisitAndAppointmentConsistent() throws Exception {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            f.authenticate(f.doctorA);
            doctorVisits.start(f.visitA);
            clinical.update(f.visitA, new UpdateClinicalVisitRequest(null, null,
                    "Synthetic diagnosis", null, null));
            SecurityContextHolder.clearContext();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            try (var pool = Executors.newFixedThreadPool(2)) {
                var complete = pool.submit(() -> finish(f, true, ready, start, success, rejected));
                var abandon = pool.submit(() -> finish(f, false, ready, start, success, rejected));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                complete.get(15, TimeUnit.SECONDS);
                abandon.get(15, TimeUnit.SECONDS);
            }
            assertEquals(1, success.get());
            assertEquals(1, rejected.get());
            assertEquals(jdbc.queryForObject("SELECT status FROM medical_visit WHERE id = ?",
                    String.class, f.visitA), jdbc.queryForObject(
                    "SELECT status FROM appointment WHERE id = ?", String.class, f.appointmentA));
        }
    }

    @Test
    void receptionistCannotCloseAppointmentWhileDoctorCompletesLinkedVisit() throws Exception {
        try (var f = OutpatientTestFixture.create(jdbc, accounts)) {
            f.authenticate(f.doctorA);
            doctorVisits.start(f.visitA);
            clinical.update(f.visitA, new UpdateClinicalVisitRequest(null, null,
                    "Synthetic diagnosis", null, null));
            SecurityContextHolder.clearContext();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger doctorSuccess = new AtomicInteger();
            AtomicInteger receptionistRejected = new AtomicInteger();
            try (var pool = Executors.newFixedThreadPool(2)) {
                var a = pool.submit(() -> finish(f, true, ready, start,
                        doctorSuccess, new AtomicInteger()));
                var b = pool.submit(() -> {
                    f.authenticate(f.receptionist);
                    ready.countDown();
                    try {
                        start.await();
                        try {
                            receptionAppointments.notComplete(f.appointmentA, "No show");
                        } catch (BusinessException error) {
                            if ("APPOINTMENT_HAS_VISIT".equals(error.getCode())
                                    || "APPOINTMENT_FINAL".equals(error.getCode())) {
                                receptionistRejected.incrementAndGet();
                            } else {
                                throw error;
                            }
                        }
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                    return null;
                });
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                a.get(15, TimeUnit.SECONDS);
                b.get(15, TimeUnit.SECONDS);
            }
            assertEquals(1, doctorSuccess.get());
            assertEquals(1, receptionistRejected.get());
            assertEquals("COMPLETED", jdbc.queryForObject(
                    "SELECT status FROM appointment WHERE id = ?", String.class, f.appointmentA));
        }
    }

    private void finish(OutpatientTestFixture f, boolean complete, CountDownLatch ready,
            CountDownLatch start, AtomicInteger success, AtomicInteger rejected) {
        f.authenticate(f.doctorA);
        ready.countDown();
        try {
            try {
                start.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            }
            if (complete) {
                clinical.complete(f.visitA);
            } else {
                clinical.notComplete(f.visitA, "Patient left");
            }
            success.incrementAndGet();
        } catch (BusinessException failure) {
            rejected.incrementAndGet();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
