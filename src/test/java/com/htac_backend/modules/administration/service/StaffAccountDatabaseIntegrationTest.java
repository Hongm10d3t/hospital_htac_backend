package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.dto.CreateStaffAccountRequest;
import com.htac_backend.modules.administration.dto.StaffAccountResponse;
import com.htac_backend.modules.administration.dto.UpdateStaffAccountRequest;
import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.shared.api.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class StaffAccountDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired StaffAccountService staff;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void doctorIsCreatedWithPrivateProfileAndLockKeepsIt() {
        long specialtyId = specialty(true);
        StaffAccountResponse doctor = staff.create(request(AccountRole.DOCTOR, specialtyId));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM doctor_profile WHERE user_id = ? AND specialty_id = ? AND is_public = FALSE",
                Long.class, doctor.id(), specialtyId));

        staff.setActive(doctor.id(), false);
        assertFalse(accounts.findById(doctor.id()).orElseThrow().isActive());
        assertFalse(staff.get(doctor.id()).active());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM doctor_profile WHERE user_id = ?",
                Long.class, doctor.id()));
        staff.setActive(doctor.id(), true);
        assertTrue(accounts.findById(doctor.id()).orElseThrow().isActive());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void failedDoctorProfileCreationRollsBackAccount() {
        long specialtyId = specialty(false);
        String username = unique("staff");
        try {
            BusinessException error = assertThrows(BusinessException.class, () -> staff.create(
                    new CreateStaffAccountRequest(username, UUID.randomUUID().toString(),
                            "Doctor", "0000000000", AccountRole.DOCTOR, specialtyId)));
            assertEquals("SPECIALTY_INACTIVE", error.getCode());
            assertFalse(accounts.existsByUsername(username));
        } finally {
            jdbc.update("DELETE FROM specialty WHERE id = ?", specialtyId);
        }
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void roleCanChangeUntilAccountHasHistoryThenItIsLocked() {
        StaffAccountResponse receptionist = staff.create(request(AccountRole.RECEPTIONIST, null));
        StaffAccountResponse pharmacist = staff.update(receptionist.id(),
                new UpdateStaffAccountRequest(null, null, null, AccountRole.PHARMACIST, null));
        assertEquals(AccountRole.PHARMACIST, pharmacist.role());

        jdbc.update("INSERT INTO specialty (code, name, created_by) VALUES (?, ?, ?)",
                unique("sp"), "Test", receptionist.id());
        BusinessException error = assertThrows(BusinessException.class, () -> staff.update(receptionist.id(),
                new UpdateStaffAccountRequest(null, null, null, AccountRole.RECEPTIONIST, null)));
        assertEquals("ROLE_LOCKED", error.getCode());
        assertEquals(AccountRole.PHARMACIST, accounts.findById(receptionist.id()).orElseThrow().getRole());
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void doctorRequiresActiveSpecialtyAndRoleChangesCreateProfile() {
        StaffAccountResponse receptionist = staff.create(request(AccountRole.RECEPTIONIST, null));
        long specialtyId = specialty(true);
        StaffAccountResponse doctor = staff.update(receptionist.id(),
                new UpdateStaffAccountRequest(null, null, null, AccountRole.DOCTOR, specialtyId));
        assertEquals(AccountRole.DOCTOR, doctor.role());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM doctor_profile WHERE user_id = ?",
                Long.class, doctor.id()));
        BusinessException error = assertThrows(BusinessException.class, () -> staff.update(doctor.id(),
                new UpdateStaffAccountRequest(null, null, null, AccountRole.PHARMACIST, null)));
        assertEquals("ROLE_LOCKED", error.getCode());
    }

    private long specialty(boolean active) {
        String code = unique("sp");
        jdbc.update("INSERT INTO specialty (code, name, is_active) VALUES (?, ?, ?)", code, "Test", active);
        return jdbc.queryForObject("SELECT id FROM specialty WHERE code = ?", Long.class, code);
    }

    private CreateStaffAccountRequest request(AccountRole role, Long specialtyId) {
        return new CreateStaffAccountRequest(unique("staff"), UUID.randomUUID().toString(),
                "Test Staff", "0000000000", role, specialtyId);
    }

    private String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 10);
    }
}
