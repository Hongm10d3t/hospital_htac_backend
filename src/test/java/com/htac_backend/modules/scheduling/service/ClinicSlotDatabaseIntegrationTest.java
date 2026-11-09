package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.clinicdirectory.dto.CreateClinicRoomRequest;
import com.htac_backend.modules.clinicdirectory.dto.CreateSpecialtyRequest;
import com.htac_backend.modules.clinicdirectory.service.ClinicRoomAdminService;
import com.htac_backend.modules.clinicdirectory.service.DoctorProfileCreationService;
import com.htac_backend.modules.clinicdirectory.service.SpecialtyAdminService;
import com.htac_backend.modules.scheduling.dto.CreateClinicSessionRequest;
import com.htac_backend.modules.scheduling.dto.CreateClinicSlotRequest;
import com.htac_backend.shared.api.BusinessException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ClinicSlotDatabaseIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired ClinicSlotAdminService slots;
    @Autowired SpecialtyAdminService specialties;
    @Autowired ClinicRoomAdminService rooms;
    @Autowired DoctorProfileCreationService doctorProfiles;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void sessionCreatesAdjacentSlotsAndRejectsAllConflicts() {
        var specialty = specialties.create(new CreateSpecialtyRequest(code("s"), "Specialty"));
        var room = rooms.create(new CreateClinicRoomRequest(specialty.id(), code("r"), "Room", null));
        UserAccount doctor = accounts.saveAndFlush(new UserAccount(code("u"),
                encoder.encode(UUID.randomUUID().toString()), "Doctor", "0000000000", AccountRole.DOCTOR));
        doctorProfiles.create(doctor.getId(), specialty.id());
        LocalDate monday = LocalDate.now(ZONE).plusWeeks(2).with(DayOfWeek.MONDAY);
        var created = slots.createSession(new CreateClinicSessionRequest(room.id(), doctor.getId(),
                List.of(monday, monday.plusDays(2)), LocalTime.of(8, 0), LocalTime.of(9, 0), 15, 2));
        assertEquals(8, created.size());
        assertEquals(created.getFirst().endAt(), created.get(1).startAt());
        assertEquals(8L, jdbc.queryForObject("SELECT COUNT(*) FROM clinic_slot WHERE room_id = ?", Long.class,
                room.id()));
        OffsetDateTime overlap = monday.atTime(8, 10).atZone(ZONE).toOffsetDateTime();
        assertEquals("SLOT_OVERLAP", assertThrows(BusinessException.class, () -> slots.create(
                new CreateClinicSlotRequest(room.id(), doctor.getId(), overlap,
                        overlap.plusMinutes(10), 1))).getCode());
        var sameSpecialtyRoom = rooms.create(new CreateClinicRoomRequest(specialty.id(), code("r"),
                "Second Room", null));
        assertEquals("SLOT_OVERLAP", assertThrows(BusinessException.class, () -> slots.create(
                new CreateClinicSlotRequest(sameSpecialtyRoom.id(), doctor.getId(), overlap,
                        overlap.plusMinutes(10), 1))).getCode());
        UserAccount secondDoctor = accounts.saveAndFlush(new UserAccount(code("u"),
                encoder.encode(UUID.randomUUID().toString()), "Second Doctor", "0000000000", AccountRole.DOCTOR));
        doctorProfiles.create(secondDoctor.getId(), specialty.id());
        assertEquals("SLOT_OVERLAP", assertThrows(BusinessException.class, () -> slots.create(
                new CreateClinicSlotRequest(room.id(), secondDoctor.getId(), overlap,
                        overlap.plusMinutes(10), 1))).getCode());
        assertEquals(8L, jdbc.queryForObject("SELECT COUNT(*) FROM clinic_slot WHERE room_id = ?", Long.class,
                room.id()));

        var fridayStart = monday.plusDays(4).atTime(10, 0).atZone(ZONE).toOffsetDateTime();
        slots.create(new CreateClinicSlotRequest(room.id(), doctor.getId(), fridayStart,
                fridayStart.plusMinutes(15), 1));
        assertEquals("SLOT_OVERLAP", assertThrows(BusinessException.class, () -> slots.createSession(
                new CreateClinicSessionRequest(room.id(), doctor.getId(),
                        List.of(monday.plusDays(2), monday.plusDays(4)), LocalTime.of(10, 0),
                        LocalTime.of(10, 15), 15, 1))).getCode());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM clinic_slot WHERE room_id = ? "
                + "AND start_at = ?", Long.class, room.id(),
                java.sql.Timestamp.from(monday.plusDays(2).atTime(10, 0).atZone(ZONE).toInstant())));

        var otherSpecialty = specialties.create(new CreateSpecialtyRequest(code("s"), "Other"));
        var otherRoom = rooms.create(new CreateClinicRoomRequest(otherSpecialty.id(), code("r"), "Room", null));
        assertEquals("SPECIALTY_MISMATCH", assertThrows(BusinessException.class, () -> slots.create(
                new CreateClinicSlotRequest(otherRoom.id(), doctor.getId(), overlap.plusHours(1),
                        overlap.plusHours(2), 1))).getCode());
        rooms.setActive(room.id(), false);
        assertEquals("ROOM_INACTIVE", assertThrows(BusinessException.class, () -> slots.create(
                new CreateClinicSlotRequest(room.id(), doctor.getId(), overlap.plusHours(1),
                        overlap.plusHours(2), 1))).getCode());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void concurrentRequestsCannotCreateOverlappingSlot() throws Exception {
        var specialty = specialties.create(new CreateSpecialtyRequest(code("s"), "Concurrent"));
        var room = rooms.create(new CreateClinicRoomRequest(specialty.id(), code("r"), "Room", null));
        UserAccount doctor = accounts.saveAndFlush(new UserAccount(code("u"),
                encoder.encode(UUID.randomUUID().toString()), "Doctor", "0000000000", AccountRole.DOCTOR));
        transactions.executeWithoutResult(status -> doctorProfiles.create(doctor.getId(), specialty.id()));
        LocalDate day = LocalDate.now(ZONE).plusWeeks(3);
        OffsetDateTime start = day.atTime(8, 0).atZone(ZONE).toOffsetDateTime();
        CreateClinicSlotRequest request = new CreateClinicSlotRequest(room.id(), doctor.getId(), start,
                start.plusMinutes(15), 1);
        SecurityContext context = SecurityContextHolder.getContext();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> job = () -> {
                SecurityContextHolder.setContext(context);
                try {
                    ready.countDown();
                    startSignal.await();
                    try {
                        slots.create(request);
                        return "CREATED";
                    } catch (BusinessException exception) {
                        return exception.getCode();
                    }
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            Future<String> first = pool.submit(job);
            Future<String> second = pool.submit(job);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            startSignal.countDown();
            assertTrue(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .containsAll(List.of("CREATED", "SLOT_OVERLAP")));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM clinic_slot WHERE room_id = ?",
                    Long.class, room.id()));
        } finally {
            jdbc.update("DELETE FROM clinic_slot WHERE room_id = ?", room.id());
            jdbc.update("DELETE FROM doctor_profile WHERE user_id = ?", doctor.getId());
            jdbc.update("DELETE FROM clinic_room WHERE id = ?", room.id());
            jdbc.update("DELETE FROM specialty WHERE id = ?", specialty.id());
            jdbc.update("DELETE FROM user_account WHERE id = ?", doctor.getId());
        }
    }

    private String code(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 10);
    }
}
