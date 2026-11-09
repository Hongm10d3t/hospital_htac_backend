package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.clinicdirectory.dto.CreateClinicRoomRequest;
import com.htac_backend.modules.clinicdirectory.dto.CreateDoctorProfileRequest;
import com.htac_backend.modules.clinicdirectory.dto.CreateSpecialtyRequest;
import com.htac_backend.modules.clinicdirectory.dto.UpdateDoctorProfileRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class DoctorProfileDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired DoctorProfileAdminService profiles;
    @Autowired SpecialtyAdminService specialties;
    @Autowired ClinicRoomAdminService rooms;
    @Autowired DoctorAssignmentLookupService assignment;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void profileRequiresDoctorAndValidSpecialtyAndIsUnique() {
        var specialty = specialties.create(new CreateSpecialtyRequest(code("s"), "Surgery"));
        UserAccount clerk = account(AccountRole.RECEPTIONIST);
        assertEquals("DOCTOR_ACCOUNT_REQUIRED", assertThrows(BusinessException.class, () ->
                profiles.create(request(clerk.getId(), specialty.id(), (short) 2010))).getCode());

        UserAccount doctor = account(AccountRole.DOCTOR);
        assertThrows(InvalidRequestException.class, () ->
                profiles.create(request(doctor.getId(), specialty.id(), (short) (Year.now().getValue() + 1))));
        var created = profiles.create(request(doctor.getId(), specialty.id(), (short) 2010));
        assertEquals("Head of unit", created.position());
        assertEquals(AccountRole.DOCTOR, accounts.findById(doctor.getId()).orElseThrow().getRole());
        assertEquals("DOCTOR_PROFILE_EXISTS", assertThrows(BusinessException.class, () ->
                profiles.create(request(doctor.getId(), specialty.id(), (short) 2010))).getCode());
        assertEquals(specialty.id(), assignment.requireActiveDoctor(doctor.getId()).specialtyId());

        specialties.setActive(specialty.id(), false);
        UserAccount another = account(AccountRole.DOCTOR);
        assertEquals("SPECIALTY_INACTIVE", assertThrows(BusinessException.class, () ->
                profiles.create(request(another.getId(), specialty.id(), (short) 2010))).getCode());
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void specialtyChangeIsLockedAfterFirstSlotButOtherContentCanChange() {
        var first = specialties.create(new CreateSpecialtyRequest(code("s"), "First"));
        var second = specialties.create(new CreateSpecialtyRequest(code("s"), "Second"));
        UserAccount doctor = account(AccountRole.DOCTOR);
        profiles.create(request(doctor.getId(), first.id(), (short) 2010));
        var updated = profiles.update(doctor.getId(), update(second.id(), "Lead doctor"));
        assertEquals(second.id(), updated.specialtyId());
        assertEquals(AccountRole.DOCTOR, accounts.findById(doctor.getId()).orElseThrow().getRole());

        var room = rooms.create(new CreateClinicRoomRequest(second.id(), code("r"), "Room", null));
        Instant start = Instant.now().plusSeconds(86400);
        jdbc.update("INSERT INTO clinic_slot (room_id, doctor_id, start_at, end_at, capacity, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", room.id(), doctor.getId(), Timestamp.from(start),
                Timestamp.from(start.plusSeconds(900)), 1, "OPEN");
        assertEquals("DOCTOR_SPECIALTY_LOCKED", assertThrows(BusinessException.class, () ->
                profiles.update(doctor.getId(), update(first.id(), "Changed"))).getCode());
        assertEquals(second.id(), profiles.get(doctor.getId()).specialtyId());
        assertEquals("Consultant", profiles.update(doctor.getId(), update(second.id(), "Consultant")).position());
    }

    private UserAccount account(AccountRole role) {
        return accounts.saveAndFlush(new UserAccount(code("u"), encoder.encode(UUID.randomUUID().toString()),
                "Test Doctor", "0000000000", role));
    }

    private CreateDoctorProfileRequest request(Long userId, Long specialtyId, Short year) {
        return new CreateDoctorProfileRequest(userId, specialtyId, "https://example.com/avatar.jpg", "MD",
                "Head of unit", year, "Intro", "Expertise", "Certificate");
    }

    private UpdateDoctorProfileRequest update(Long specialtyId, String position) {
        return new UpdateDoctorProfileRequest(specialtyId, null, "MD", position, (short) 2010,
                "Intro", "Expertise", "Certificate");
    }

    private String code(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 10);
    }
}
