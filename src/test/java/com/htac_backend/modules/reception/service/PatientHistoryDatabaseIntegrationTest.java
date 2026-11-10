package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.administration.dto.RegisterPatientRequest;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.administration.service.PatientAccountRegistrationService;
import com.htac_backend.modules.reception.dto.IssueRegistrationVerificationRequest;
import com.htac_backend.modules.reception.entity.PatientGender;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class PatientHistoryDatabaseIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired PatientHistoryService history;
    @Autowired PatientRegistrationLinkService links;
    @Autowired PatientAccountRegistrationService registrations;
    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void linkedPatientSeesOldAndNewVisitsOnlyOwnIssuedPrescriptions() {
        Fixture f = fixture();
        String username = "h" + f.tag();
        Long accountId = null;
        try {
            var token = links.issueVerification(f.patientId(), new IssueRegistrationVerificationRequest(
                    f.citizenId(), "Synthetic History", LocalDate.of(1990, 1, 2)));
            var registered = registrations.register(new RegisterPatientRequest(username,
                    UUID.randomUUID().toString(), "Synthetic History", "0000000000", f.citizenId(),
                    LocalDate.of(1990, 1, 2), PatientGender.OTHER, null, token.token()));
            accountId = registered.accountId();
            assertEquals(f.patientId(), registered.patientId());
            insertVisit(f, "new", f.patientId(), f.todaySlot(), LocalDate.now(ZONE), 1, "WAITING", null);
            long newVisit = id("SELECT id FROM medical_visit WHERE visit_code = ?", "new" + f.tag());
            jdbc.update("UPDATE medical_visit SET diagnosis = ? WHERE id = ?", "draft conclusion", newVisit);
            jdbc.update("INSERT INTO prescription (prescription_code, visit_id, doctor_id, status) "
                    + "VALUES (?, ?, ?, 'DRAFT')", "draft" + f.tag(), newVisit, f.doctorId());
            authenticate(accountId);
            var all = history.visits(null, null, page("arrivedAt"));
            assertEquals(2, all.totalItems());
            assertEquals(f.oldVisit(), all.items().get(1).id());
            assertEquals(newVisit, history.visits(LocalDate.now(ZONE), null, page("arrivedAt"))
                    .items().getFirst().id());
            assertEquals(f.oldVisit(), history.visits(null, LocalDate.now(ZONE).minusDays(1),
                    page("arrivedAt")).items().getFirst().id());
            assertEquals("Prior diagnosis", history.visit(f.oldVisit()).visit().diagnosis());
            assertNull(history.visit(newVisit).visit().diagnosis());
            assertEquals(1, history.visit(f.oldVisit()).prescriptions().size());
            assertEquals(1, history.prescriptions(null, null, page("issuedAt")).totalItems());
            assertEquals("Synthetic medicine", history.prescription(f.issuedPrescription())
                    .items().getFirst().medicineName());
            assertThrows(NotFoundException.class, () -> history.visit(f.otherVisit()));
            assertThrows(NotFoundException.class, () -> history.prescription(f.otherPrescription()));
            long draft = id("SELECT id FROM prescription WHERE prescription_code = ?", "draft" + f.tag());
            assertThrows(NotFoundException.class, () -> history.prescription(draft));
        } finally {
            SecurityContextHolder.clearContext();
            cleanup(f, accountId);
        }
    }

    private PageQuery page(String field) {
        return PageQuery.parse(0, 20, field + ",DESC", field, Set.of(field));
    }

    private Fixture fixture() {
        String tag = UUID.randomUUID().toString().substring(0, 12);
        String citizenId = "%012d".formatted(Math.floorMod(UUID.randomUUID().getMostSignificantBits(),
                1_000_000_000_000L));
        jdbc.update("INSERT INTO specialty (code, name) VALUES (?, ?)", "s" + tag, "History specialty");
        long specialtyId = id("SELECT id FROM specialty WHERE code = ?", "s" + tag);
        jdbc.update("INSERT INTO clinic_room (specialty_id, code, name) VALUES (?, ?, ?)",
                specialtyId, "r" + tag, "History room");
        long roomId = id("SELECT id FROM clinic_room WHERE code = ?", "r" + tag);
        jdbc.update("INSERT INTO user_account (username, password_hash, full_name, phone, role) "
                + "VALUES (?, '{noop}unused', 'Synthetic doctor', '0000000000', 'DOCTOR')", "d" + tag);
        long doctorId = id("SELECT id FROM user_account WHERE username = ?", "d" + tag);
        jdbc.update("INSERT INTO doctor_profile (user_id, specialty_id) VALUES (?, ?)", doctorId, specialtyId);
        LocalDate yesterday = LocalDate.now(ZONE).minusDays(1);
        long oldSlot = slot(roomId, doctorId, yesterday);
        long todaySlot = slot(roomId, doctorId, LocalDate.now(ZONE));
        jdbc.update("INSERT INTO patient (patient_code, citizen_id, full_name, date_of_birth, gender) "
                + "VALUES (?, ?, 'Synthetic History', ?, 'OTHER')", "p" + tag, citizenId,
                LocalDate.of(1990, 1, 2));
        jdbc.update("INSERT INTO patient (patient_code, full_name, date_of_birth, gender) "
                + "VALUES (?, 'Other Patient', ?, 'OTHER')", "o" + tag, LocalDate.of(1988, 1, 1));
        long patientId = id("SELECT id FROM patient WHERE patient_code = ?", "p" + tag);
        long otherPatientId = id("SELECT id FROM patient WHERE patient_code = ?", "o" + tag);
        Fixture base = new Fixture(tag, citizenId, specialtyId, roomId, doctorId, oldSlot, todaySlot,
                patientId, otherPatientId, 0, 0, 0, 0, 0);
        insertVisit(base, "old", patientId, oldSlot, yesterday, 1, "COMPLETED", "Prior diagnosis");
        insertVisit(base, "other", otherPatientId, oldSlot, yesterday, 2, "COMPLETED", "Private diagnosis");
        long oldVisit = id("SELECT id FROM medical_visit WHERE visit_code = ?", "old" + tag);
        long otherVisit = id("SELECT id FROM medical_visit WHERE visit_code = ?", "other" + tag);
        jdbc.update("INSERT INTO medicine (code, name, active_ingredient, strength, dosage_form, base_unit) "
                + "VALUES (?, 'Synthetic medicine', 'Ingredient', '1 mg', 'tablet', 'tablet')", "m" + tag);
        long medicineId = id("SELECT id FROM medicine WHERE code = ?", "m" + tag);
        long issued = prescription("issued" + tag, oldVisit, doctorId);
        long other = prescription("other" + tag, otherVisit, doctorId);
        jdbc.update("INSERT INTO prescription_item (prescription_id, medicine_id, quantity, usage_instruction) "
                + "VALUES (?, ?, 2, 'Synthetic instruction')", issued, medicineId);
        return new Fixture(tag, citizenId, specialtyId, roomId, doctorId, oldSlot, todaySlot,
                patientId, otherPatientId, oldVisit, otherVisit, issued, other, medicineId);
    }

    private long slot(long roomId, long doctorId, LocalDate date) {
        Instant start = date.atStartOfDay(ZONE).toInstant();
        jdbc.update("INSERT INTO clinic_slot (room_id, doctor_id, start_at, end_at, capacity) "
                + "VALUES (?, ?, ?, ?, 10)", roomId, doctorId, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(3600)));
        return jdbc.queryForObject("SELECT id FROM clinic_slot WHERE room_id = ? AND start_at = ?",
                Long.class, roomId, Timestamp.from(start));
    }

    private void insertVisit(Fixture f, String prefix, long patientId, long slotId,
            LocalDate date, int queue, String status, String diagnosis) {
        Instant arrived = date.atStartOfDay(ZONE).toInstant();
        jdbc.update("INSERT INTO medical_visit (visit_code, patient_id, slot_id, room_id, queue_date, "
                + "queue_number, arrived_at, status, diagnosis) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                prefix + f.tag(), patientId, slotId, f.roomId(), date, queue,
                Timestamp.from(arrived), status, diagnosis);
    }

    private long prescription(String code, long visitId, long doctorId) {
        jdbc.update("INSERT INTO prescription (prescription_code, visit_id, doctor_id, issued_at, status) "
                + "VALUES (?, ?, ?, ?, 'ISSUED')", code, visitId, doctorId, Timestamp.from(Instant.now()));
        return id("SELECT id FROM prescription WHERE prescription_code = ?", code);
    }

    private void authenticate(Long accountId) {
        AccountPrincipal principal = new AccountPrincipal(accounts.findById(accountId).orElseThrow());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private long id(String query, Object parameter) {
        return jdbc.queryForObject(query, Long.class, parameter);
    }

    private void cleanup(Fixture f, Long accountId) {
        jdbc.update("DELETE FROM prescription_item WHERE prescription_id IN (SELECT id FROM prescription "
                + "WHERE prescription_code IN (?, ?, ?))", "issued" + f.tag(), "other" + f.tag(),
                "draft" + f.tag());
        jdbc.update("DELETE FROM prescription WHERE prescription_code IN (?, ?, ?)",
                "issued" + f.tag(), "other" + f.tag(), "draft" + f.tag());
        jdbc.update("DELETE FROM medical_visit WHERE slot_id IN (?, ?)", f.oldSlot(), f.todaySlot());
        jdbc.update("DELETE FROM patient WHERE id IN (?, ?)", f.patientId(), f.otherPatientId());
        jdbc.update("DELETE FROM clinic_slot WHERE id IN (?, ?)", f.oldSlot(), f.todaySlot());
        jdbc.update("DELETE FROM doctor_profile WHERE user_id = ?", f.doctorId());
        jdbc.update("DELETE FROM medicine WHERE id = ?", f.medicineId());
        jdbc.update("DELETE FROM clinic_room WHERE id = ?", f.roomId());
        jdbc.update("DELETE FROM specialty WHERE id = ?", f.specialtyId());
        jdbc.update("DELETE FROM user_account WHERE id = ?", f.doctorId());
        if (accountId != null) {
            jdbc.update("DELETE FROM user_account WHERE id = ?", accountId);
        }
    }

    private record Fixture(String tag, String citizenId, long specialtyId, long roomId, long doctorId,
            long oldSlot, long todaySlot, long patientId, long otherPatientId, long oldVisit,
            long otherVisit, long issuedPrescription, long otherPrescription, long medicineId) { }
}
