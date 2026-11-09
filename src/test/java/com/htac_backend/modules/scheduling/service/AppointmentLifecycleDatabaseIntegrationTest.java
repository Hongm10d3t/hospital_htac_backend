package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.clinicdirectory.dto.CreateClinicRoomRequest;
import com.htac_backend.modules.clinicdirectory.dto.CreateSpecialtyRequest;
import com.htac_backend.modules.clinicdirectory.service.ClinicRoomAdminService;
import com.htac_backend.modules.clinicdirectory.service.DoctorProfileCreationService;
import com.htac_backend.modules.clinicdirectory.service.SpecialtyAdminService;
import com.htac_backend.modules.reception.entity.Patient;
import com.htac_backend.modules.reception.entity.PatientGender;
import com.htac_backend.modules.reception.repository.PatientRepository;
import com.htac_backend.modules.scheduling.dto.CreateClinicSlotRequest;
import com.htac_backend.modules.scheduling.entity.Appointment;
import com.htac_backend.modules.scheduling.repository.AppointmentRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class AppointmentLifecycleDatabaseIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired ReceptionAppointmentService reception;
    @Autowired PatientAppointmentService own;
    @Autowired ClinicSlotAdminService slots;
    @Autowired SpecialtyAdminService specialties;
    @Autowired ClinicRoomAdminService rooms;
    @Autowired DoctorProfileCreationService doctorProfiles;
    @Autowired UserAccountRepository accounts;
    @Autowired PatientRepository patients;
    @Autowired AppointmentRepository appointments;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void receptionistConfirmsAndPatientCanReadOnlyOwnAppointment() {
        Fixture fixture = fixture();
        UserAccount otherPatient = patientAccount();
        authenticate(fixture.receptionist());
        var confirmed = reception.confirm(fixture.appointmentId());
        assertEquals("CONFIRMED", confirmed.appointment().status().name());
        assertEquals("0000000000", confirmed.phone());
        assertNotNull(confirmed.appointment().confirmedAt());
        assertEquals(fixture.receptionist().getId(), appointments.findById(fixture.appointmentId())
                .orElseThrow().getConfirmedBy());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM medical_visit WHERE appointment_id = ?",
                Long.class, fixture.appointmentId()));
        assertEquals(1, reception.list(null, null, page()).totalItems());

        authenticate(otherPatient);
        assertThrows(NotFoundException.class, () -> own.ownGet(fixture.appointmentId()));
        assertEquals(0, own.ownList(page()).totalItems());
        authenticate(fixture.patient());
        assertEquals("CONFIRMED", own.ownGet(fixture.appointmentId()).status().name());
        assertEquals(1, own.ownList(page()).totalItems());

        authenticate(fixture.receptionist());
        var closed = reception.notComplete(fixture.appointmentId(), "Did not arrive");
        assertEquals("NOT_COMPLETED", closed.appointment().status().name());
        assertNotNull(closed.appointment().closedAt());
        assertEquals("APPOINTMENT_FINAL", assertThrows(BusinessException.class,
                () -> reception.confirm(fixture.appointmentId())).getCode());
        assertEquals("APPOINTMENT_FINAL", assertThrows(BusinessException.class,
                () -> reception.notComplete(fixture.appointmentId(), "Changed reason")).getCode());
        assertEquals("NOT_COMPLETED", reception.notComplete(fixture.appointmentId(),
                "Did not arrive").appointment().status().name());
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void completedAndAbandonedVisitsDriveFinalAppointmentStatus() {
        Fixture fixture = fixture();
        authenticate(fixture.receptionist());
        assertEquals("VISIT_REQUIRED", assertThrows(BusinessException.class,
                () -> reception.complete(fixture.appointmentId())).getCode());
        insertVisit(fixture, fixture.appointmentId(), 1, "COMPLETED", null);
        assertEquals("COMPLETED", reception.complete(fixture.appointmentId()).appointment().status().name());
        assertEquals("COMPLETED", reception.complete(fixture.appointmentId()).appointment().status().name());
        assertEquals("APPOINTMENT_FINAL", assertThrows(BusinessException.class, () ->
                reception.notComplete(fixture.appointmentId(), "No show")).getCode());

        Appointment second = appointments.saveAndFlush(new Appointment(code("A"), fixture.patientId(),
                fixture.slotId(), "Another reason"));
        insertVisit(fixture, second.getId(), 2, "NOT_COMPLETED", "Patient left");
        reception.notCompleteFromVisit(second.getId());
        assertEquals("NOT_COMPLETED", appointments.findById(second.getId()).orElseThrow().getStatus().name());
        assertEquals("Patient left", appointments.findById(second.getId())
                .orElseThrow().getNonCompletionReason());
        assertEquals("APPOINTMENT_FINAL", assertThrows(BusinessException.class,
                () -> reception.completeFromVisit(second.getId())).getCode());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void competingFinalUpdatesCannotOverwriteReason() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> first = closeJob(fixture, "First reason", ready, release);
            Callable<String> second = closeJob(fixture, "Second reason", ready, release);
            var resultA = pool.submit(first);
            var resultB = pool.submit(second);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            release.countDown();
            var results = List.of(resultA.get(10, TimeUnit.SECONDS), resultB.get(10, TimeUnit.SECONDS));
            assertTrue(results.containsAll(List.of("NOT_COMPLETED", "APPOINTMENT_FINAL")),
                    results.toString());
            String reason = jdbc.queryForObject("SELECT non_completion_reason FROM appointment WHERE id = ?",
                    String.class, fixture.appointmentId());
            assertTrue(reason.equals("First reason") || reason.equals("Second reason"));
        } finally {
            jdbc.update("DELETE FROM appointment WHERE id = ?", fixture.appointmentId());
            jdbc.update("DELETE FROM clinic_slot WHERE id = ?", fixture.slotId());
            jdbc.update("DELETE FROM patient WHERE id = ?", fixture.patientId());
            jdbc.update("DELETE FROM doctor_profile WHERE user_id = ?", fixture.doctorId());
            jdbc.update("DELETE FROM clinic_room WHERE id = ?", fixture.roomId());
            jdbc.update("DELETE FROM specialty WHERE id = ?", fixture.specialtyId());
            jdbc.update("DELETE FROM user_account WHERE id IN (?, ?, ?)", fixture.doctorId(),
                    fixture.patient().getId(), fixture.receptionist().getId());
        }
    }

    private Callable<String> closeJob(Fixture fixture, String reason, CountDownLatch ready,
            CountDownLatch release) {
        return () -> {
            authenticate(fixture.receptionist());
            try {
                ready.countDown();
                release.await();
                try {
                    return reception.notComplete(fixture.appointmentId(), reason).appointment().status().name();
                } catch (BusinessException exception) {
                    return exception.getCode();
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private Fixture fixture() {
        var specialty = specialties.create(new CreateSpecialtyRequest(code("s"), "Specialty"));
        var room = rooms.create(new CreateClinicRoomRequest(specialty.id(), code("r"), "Room", null));
        UserAccount doctor = account(AccountRole.DOCTOR);
        transactions.executeWithoutResult(status -> doctorProfiles.create(doctor.getId(), specialty.id()));
        UserAccount patientAccount = patientAccount();
        Long patientId = patients.findByUserId(patientAccount.getId()).orElseThrow().getId();
        UserAccount receptionist = account(AccountRole.RECEPTIONIST);
        var start = LocalDate.now(ZONE).plusDays(1).atTime(8, 0).atZone(ZONE).toOffsetDateTime();
        var slot = slots.create(new CreateClinicSlotRequest(room.id(), doctor.getId(), start,
                start.plusMinutes(15), 3));
        Appointment appointment = appointments.saveAndFlush(new Appointment(code("A"), patientId,
                slot.id(), "Checkup"));
        return new Fixture(specialty.id(), room.id(), doctor.getId(), patientId, slot.id(),
                appointment.getId(), patientAccount, receptionist);
    }

    private UserAccount patientAccount() {
        UserAccount account = account(AccountRole.PATIENT);
        patients.saveAndFlush(new Patient(code("P"), account.getId(), null, "Patient",
                LocalDate.of(1990, 1, 1), PatientGender.OTHER, null));
        return account;
    }

    private UserAccount account(AccountRole role) {
        return accounts.saveAndFlush(new UserAccount(code("u"), encoder.encode(UUID.randomUUID().toString()),
                role.name(), "0000000000", role));
    }

    private void insertVisit(Fixture fixture, Long appointmentId, int queue, String status, String outcome) {
        jdbc.update("INSERT INTO medical_visit (visit_code, patient_id, appointment_id, slot_id, room_id, "
                        + "queue_date, queue_number, arrived_at, status, diagnosis, outcome_reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", code("V"), fixture.patientId(),
                appointmentId, fixture.slotId(), fixture.roomId(), LocalDate.now(ZONE).plusDays(1),
                queue, Timestamp.from(Instant.now()), status,
                "COMPLETED".equals(status) ? "Diagnosis" : null, outcome);
    }

    private void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private PageQuery page() { return PageQuery.parse(0, 20, null, "createdAt", Set.of("createdAt")); }
    private String code(String prefix) { return prefix + UUID.randomUUID().toString().substring(0, 10); }
    private record Fixture(Long specialtyId, Long roomId, Long doctorId, Long patientId,
            Long slotId, Long appointmentId, UserAccount patient, UserAccount receptionist) { }
}
