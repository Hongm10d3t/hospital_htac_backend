package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.clinicdirectory.dto.CreateClinicRoomRequest;
import com.htac_backend.modules.clinicdirectory.dto.CreateSpecialtyRequest;
import com.htac_backend.modules.clinicdirectory.dto.UpdateClinicRoomRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class ClinicDirectoryDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired SpecialtyAdminService specialties;
    @Autowired ClinicRoomAdminService rooms;
    @Autowired ClinicDirectoryLookupService lookup;
    @Autowired DoctorProfileCreationService doctorProfiles;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void codesAreUniqueAndRoomsRequireActiveSpecialty() {
        String specialtyCode = code("s");
        var specialty = specialties.create(new CreateSpecialtyRequest(specialtyCode, "Synthetic Specialty"));
        BusinessException duplicate = assertThrows(BusinessException.class, () -> specialties.create(
                new CreateSpecialtyRequest(specialtyCode, "Another Name")));
        assertEquals("SPECIALTY_CODE_TAKEN", duplicate.getCode());

        assertThrows(NotFoundException.class, () -> rooms.create(
                new CreateClinicRoomRequest(Long.MAX_VALUE, code("r"), "Room", null)));
        String roomCode = code("r");
        var room = rooms.create(new CreateClinicRoomRequest(specialty.id(), roomCode, "Room", "Floor 1"));
        assertEquals(specialty.id(), lookup.requireActiveRoom(room.id()).specialtyId());
        BusinessException duplicateRoom = assertThrows(BusinessException.class, () -> rooms.create(
                new CreateClinicRoomRequest(specialty.id(), roomCode, "Another Room", null)));
        assertEquals("ROOM_CODE_TAKEN", duplicateRoom.getCode());
        assertEquals(1, rooms.list(specialty.id(), true,
                PageQuery.parse(0, 20, null, "id", Set.of("id"))).items().size());

        specialties.setActive(specialty.id(), false);
        BusinessException inactive = assertThrows(BusinessException.class, () -> rooms.create(
                new CreateClinicRoomRequest(specialty.id(), code("r"), "Room", null)));
        assertEquals("SPECIALTY_INACTIVE", inactive.getCode());
        assertEquals("SPECIALTY_INACTIVE", assertThrows(BusinessException.class,
                () -> lookup.requireActiveRoom(room.id())).getCode());
        assertFalse(specialties.get(specialty.id()).active());
        assertEquals(specialty.id(), rooms.get(room.id()).specialtyId());
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void roomMayChangeSpecialtyBeforeSlotButNotAfterHistoryExists() {
        var first = specialties.create(new CreateSpecialtyRequest(code("s"), "First"));
        var second = specialties.create(new CreateSpecialtyRequest(code("s"), "Second"));
        String roomCode = code("r");
        var room = rooms.create(new CreateClinicRoomRequest(first.id(), roomCode, "Room", null));
        var moved = rooms.update(room.id(), new UpdateClinicRoomRequest(second.id(), roomCode, "Room", null));
        assertEquals(second.id(), moved.specialtyId());

        UserAccount doctor = accounts.saveAndFlush(new UserAccount(code("u"),
                encoder.encode(UUID.randomUUID().toString()), "Synthetic Doctor", "0000000000", AccountRole.DOCTOR));
        doctorProfiles.create(doctor.getId(), second.id());
        Instant start = Instant.now().plusSeconds(86400);
        jdbc.update("INSERT INTO clinic_slot (room_id, doctor_id, start_at, end_at, capacity, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", room.id(), doctor.getId(), Timestamp.from(start),
                Timestamp.from(start.plusSeconds(900)), 1, "OPEN");

        BusinessException locked = assertThrows(BusinessException.class, () -> rooms.update(room.id(),
                new UpdateClinicRoomRequest(first.id(), roomCode, "Moved Again", null)));
        assertEquals("ROOM_SPECIALTY_LOCKED", locked.getCode());
        assertEquals(second.id(), rooms.get(room.id()).specialtyId());
        rooms.setActive(room.id(), false);
        assertEquals("ROOM_INACTIVE", assertThrows(BusinessException.class,
                () -> lookup.requireActiveRoom(room.id())).getCode());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM clinic_slot WHERE room_id = ?",
                Long.class, room.id()));
        specialties.setActive(second.id(), false);
        assertEquals("SPECIALTY_INACTIVE", assertThrows(BusinessException.class,
                () -> rooms.setActive(room.id(), true)).getCode());
        assertTrue(rooms.get(room.id()).id() > 0);
    }

    private String code(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 10);
    }
}
