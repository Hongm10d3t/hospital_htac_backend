package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.HospitalConfig;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.HospitalConfigRepository;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.HospitalConfigService;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.clinicdirectory.dto.CreateClinicRoomRequest;
import com.htac_backend.modules.clinicdirectory.dto.CreateSpecialtyRequest;
import com.htac_backend.modules.clinicdirectory.service.ClinicRoomAdminService;
import com.htac_backend.modules.clinicdirectory.service.DoctorProfileAdminService;
import com.htac_backend.modules.clinicdirectory.service.DoctorProfileCreationService;
import com.htac_backend.modules.clinicdirectory.service.SpecialtyAdminService;
import com.htac_backend.modules.reception.entity.Patient;
import com.htac_backend.modules.reception.entity.PatientGender;
import com.htac_backend.modules.reception.repository.PatientRepository;
import com.htac_backend.modules.scheduling.dto.BookAppointmentRequest;
import com.htac_backend.modules.scheduling.dto.CreateClinicSlotRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class AppointmentBookingDatabaseIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired AppointmentBookingService booking;
    @Autowired PublicClinicSlotService publicSlots;
    @Autowired ClinicSlotAdminService slots;
    @Autowired SpecialtyAdminService specialties;
    @Autowired ClinicRoomAdminService rooms;
    @Autowired DoctorProfileCreationService doctorProfiles;
    @Autowired DoctorProfileAdminService doctorAdmin;
    @Autowired UserAccountRepository accounts;
    @Autowired PatientRepository patients;
    @Autowired HospitalConfigRepository configs;
    @Autowired HospitalConfigService hospitalConfig;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void publicSearchAndBookingRespectIdentityCapacityAndStatus() {
        ensureConfig();
        Fixture fixture = fixture(1);
        UserAccount first = patientAccount();
        UserAccount second = patientAccount();
        assertEquals(1, publicSlots.search(fixture.date(), fixture.specialtyId(), fixture.doctorId(), page())
                .totalItems());
        assertEquals(0, publicSlots.search(fixture.date(), fixture.specialtyId() + 1000,
                fixture.doctorId(), page()).totalItems());
        doctorAdmin.setPublic(fixture.doctorId(), false);
        assertEquals(0, publicSlots.search(fixture.date(), fixture.specialtyId(), fixture.doctorId(), page())
                .totalItems());
        authenticate(first);
        assertThrows(NotFoundException.class, () -> booking.book(new BookAppointmentRequest(
                fixture.slotId(), "Checkup")));
        authenticateAdmin();
        doctorAdmin.setPublic(fixture.doctorId(), true);
        LocalDate outside = LocalDate.now(ZONE).plusDays((long) hospitalConfig.maxAdvanceBookingDays() + 1);
        var outsideStart = outside.atTime(8, 0).atZone(ZONE).toOffsetDateTime();
        Long outsideSlotId = slots.create(new CreateClinicSlotRequest(fixture.roomId(), fixture.doctorId(),
                outsideStart, outsideStart.plusMinutes(15), 1)).id();
        authenticate(first);
        assertEquals("SLOT_OUTSIDE_BOOKING_WINDOW", assertThrows(BusinessException.class, () ->
                booking.book(new BookAppointmentRequest(outsideSlotId, "Checkup"))).getCode());
        var created = booking.book(new BookAppointmentRequest(fixture.slotId(), "Checkup"));
        assertEquals("PENDING", created.status().name());
        assertEquals("APPOINTMENT_ALREADY_EXISTS", assertThrows(BusinessException.class, () ->
                booking.book(new BookAppointmentRequest(fixture.slotId(), "Checkup"))).getCode());
        assertEquals(0, publicSlots.search(fixture.date(), fixture.specialtyId(), fixture.doctorId(), page())
                .totalItems());
        jdbc.update("UPDATE appointment SET status = 'CONFIRMED' WHERE id = ?", created.id());
        assertEquals(0, publicSlots.search(fixture.date(), fixture.specialtyId(), fixture.doctorId(), page())
                .totalItems());
        authenticate(second);
        assertEquals("SLOT_FULL", assertThrows(BusinessException.class, () ->
                booking.book(new BookAppointmentRequest(fixture.slotId(), "Checkup"))).getCode());
        authenticateAdmin();
        slots.close(fixture.slotId());
        authenticate(second);
        assertEquals("SLOT_CLOSED", assertThrows(BusinessException.class, () ->
                booking.book(new BookAppointmentRequest(fixture.slotId(), "Checkup"))).getCode());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM appointment WHERE slot_id = ?",
                Long.class, fixture.slotId()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void twoPatientsCompeteForLastPlaceAndOnlyOneBookingCommits() throws Exception {
        boolean configCreated = ensureConfig();
        Fixture fixture = fixture(1);
        UserAccount first = patientAccount();
        UserAccount second = patientAccount();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> a = bookingJob(first, fixture.slotId(), ready, release);
            Callable<String> b = bookingJob(second, fixture.slotId(), ready, release);
            var firstResult = pool.submit(a);
            var secondResult = pool.submit(b);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            release.countDown();
            var results = List.of(firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS));
            assertTrue(results.containsAll(List.of("PENDING", "SLOT_FULL")), results.toString());
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM appointment WHERE slot_id = ?",
                    Long.class, fixture.slotId()));
        } finally {
            jdbc.update("DELETE FROM appointment WHERE slot_id = ?", fixture.slotId());
            jdbc.update("DELETE FROM clinic_slot WHERE id = ?", fixture.slotId());
            jdbc.update("DELETE FROM patient WHERE user_id IN (?, ?)", first.getId(), second.getId());
            jdbc.update("DELETE FROM doctor_profile WHERE user_id = ?", fixture.doctorId());
            jdbc.update("DELETE FROM clinic_room WHERE id = ?", fixture.roomId());
            jdbc.update("DELETE FROM specialty WHERE id = ?", fixture.specialtyId());
            jdbc.update("DELETE FROM user_account WHERE id IN (?, ?, ?)", first.getId(), second.getId(),
                    fixture.doctorId());
            if (configCreated) {
                jdbc.update("DELETE FROM hospital_config WHERE id = 1");
            }
        }
    }

    private Callable<String> bookingJob(UserAccount account, Long slotId, CountDownLatch ready,
            CountDownLatch release) {
        return () -> {
            authenticate(account);
            try {
                ready.countDown();
                release.await();
                try {
                    return booking.book(new BookAppointmentRequest(slotId, "Checkup")).status().name();
                } catch (BusinessException exception) {
                    return exception.getCode();
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private boolean ensureConfig() {
        if (configs.existsById(1L)) {
            return false;
        }
        configs.saveAndFlush(new HospitalConfig("Test Hospital", "Address", "0000000000", null,
                null, null, 30));
        return true;
    }

    private Fixture fixture(int maximum) {
        var specialty = specialties.create(new CreateSpecialtyRequest(code("s"), "Specialty"));
        var room = rooms.create(new CreateClinicRoomRequest(specialty.id(), code("r"), "Room", null));
        UserAccount doctor = accounts.saveAndFlush(new UserAccount(code("u"),
                encoder.encode(UUID.randomUUID().toString()), "Doctor", "0000000000", AccountRole.DOCTOR));
        transactions.executeWithoutResult(status -> doctorProfiles.create(doctor.getId(), specialty.id()));
        doctorAdmin.setPublic(doctor.getId(), true);
        LocalDate date = LocalDate.now(ZONE).plusDays(1);
        var start = date.atTime(8, 0).atZone(ZONE).toOffsetDateTime();
        var slot = slots.create(new CreateClinicSlotRequest(room.id(), doctor.getId(), start,
                start.plusMinutes(15), maximum));
        return new Fixture(slot.id(), room.id(), specialty.id(), doctor.getId(), date);
    }

    private UserAccount patientAccount() {
        UserAccount account = accounts.saveAndFlush(new UserAccount(code("u"),
                encoder.encode(UUID.randomUUID().toString()), "Patient", "0000000000", AccountRole.PATIENT));
        patients.saveAndFlush(new Patient(code("P"), account.getId(), null, "Patient",
                LocalDate.of(1990, 1, 1), PatientGender.OTHER, null));
        return account;
    }

    private void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private void authenticateAdmin() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("admin", null,
                List.of(() -> "ROLE_ADMIN")));
        SecurityContextHolder.setContext(context);
    }

    private PageQuery page() { return PageQuery.parse(0, 20, null, "startAt", Set.of("startAt")); }
    private String code(String prefix) { return prefix + UUID.randomUUID().toString().substring(0, 10); }
    private record Fixture(Long slotId, Long roomId, Long specialtyId, Long doctorId, LocalDate date) { }
}
