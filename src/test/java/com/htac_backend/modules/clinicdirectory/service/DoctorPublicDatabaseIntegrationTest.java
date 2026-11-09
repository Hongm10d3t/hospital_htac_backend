package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.clinicdirectory.dto.CreateDoctorProfileRequest;
import com.htac_backend.modules.clinicdirectory.dto.CreateSpecialtyRequest;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class DoctorPublicDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired DoctorProfileAdminService admin;
    @Autowired DoctorPublicService publicDoctors;
    @Autowired SpecialtyAdminService specialties;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder encoder;

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void onlyPublishedActiveDoctorsAppearAndSpecialtyFilterIsExact() {
        var first = specialties.create(new CreateSpecialtyRequest(code("s"), "First"));
        var second = specialties.create(new CreateSpecialtyRequest(code("s"), "Second"));
        UserAccount visible = doctor("Visible Doctor");
        UserAccount hidden = doctor("Hidden Doctor");
        UserAccount other = doctor("Other Doctor");
        admin.create(request(visible.getId(), first.id()));
        admin.create(request(hidden.getId(), first.id()));
        admin.create(request(other.getId(), second.id()));
        admin.setPublic(visible.getId(), true);
        admin.setPublic(other.getId(), true);

        var firstPage = publicDoctors.list(first.id(), page());
        assertEquals(1, firstPage.totalItems());
        assertEquals("Visible Doctor", firstPage.items().getFirst().fullName());
        assertEquals("First", firstPage.items().getFirst().specialtyName());
        assertTrue(firstPage.items().getFirst().availableSlotsUrl().endsWith("doctorId=" + visible.getId()));
        assertEquals(2, publicDoctors.list(null, page()).totalItems());
        assertThrows(NotFoundException.class, () -> publicDoctors.get(hidden.getId()));

        admin.setPublic(visible.getId(), false);
        assertEquals(0, publicDoctors.list(first.id(), page()).totalItems());
        assertThrows(NotFoundException.class, () -> publicDoctors.get(visible.getId()));
        accounts.findById(other.getId()).orElseThrow().setActive(false);
        accounts.flush();
        assertEquals(0, publicDoctors.list(null, page()).totalItems());
        assertThrows(NotFoundException.class, () -> publicDoctors.get(other.getId()));
        assertEquals("DOCTOR_INACTIVE", assertThrows(BusinessException.class,
                () -> admin.setPublic(other.getId(), true)).getCode());
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void inactiveSpecialtyHidesPublishedDoctor() {
        var specialty = specialties.create(new CreateSpecialtyRequest(code("s"), "Inactive"));
        UserAccount doctor = doctor("Doctor");
        admin.create(request(doctor.getId(), specialty.id()));
        admin.setPublic(doctor.getId(), true);
        specialties.setActive(specialty.id(), false);
        assertEquals(0, publicDoctors.list(null, page()).totalItems());
        assertThrows(NotFoundException.class, () -> publicDoctors.get(doctor.getId()));
        assertEquals("SPECIALTY_INACTIVE", assertThrows(BusinessException.class,
                () -> admin.setPublic(doctor.getId(), true)).getCode());
    }

    private UserAccount doctor(String fullName) {
        return accounts.saveAndFlush(new UserAccount(code("u"), encoder.encode(UUID.randomUUID().toString()),
                fullName, "0000000000", AccountRole.DOCTOR));
    }

    private CreateDoctorProfileRequest request(Long userId, Long specialtyId) {
        return new CreateDoctorProfileRequest(userId, specialtyId, null, "MD", null, (short) 2010,
                "Intro", "Expertise", "Certification");
    }

    private PageQuery page() {
        return PageQuery.parse(0, 20, null, "userId", Set.of("userId"));
    }

    private String code(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 10);
    }
}
