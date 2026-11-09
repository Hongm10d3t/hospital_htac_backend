package com.htac_backend;

import com.htac_backend.security.AuthenticatedActor;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class DatabaseIntegrationTest {
    private static final Set<String> TABLES = Set.of(
            "user_account", "patient", "doctor_profile", "specialty", "clinic_room", "clinic_slot",
            "appointment", "medical_visit", "prescription", "prescription_item", "medicine",
            "medicine_batch", "supplier", "stock_document", "stock_document_item",
            "purchase_request", "purchase_request_item", "hospital_config",
            "account_password_reset_audit");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;

    @Test
    void protectedAndMissingApiRoutesUseTheCommonErrorContract() throws Exception {
        mvc.perform(get("/api/v1/private/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/private/probe").with(user("staff")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/public/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void migrationCreatesAllTablesAndCanRunAgain() {
        Set<String> actual = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() " +
                "AND table_name <> 'flyway_schema_history'", String.class).stream().collect(Collectors.toSet());
        assertEquals(TABLES, actual);
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void uniquenessAndMidTransactionFailureRollBack() {
        String username = "n00_" + UUID.randomUUID().toString().substring(0, 8);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(DataIntegrityViolationException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO user_account (username,password_hash,full_name,phone,role) VALUES (?,?,?,?,?)",
                    username, "hash", "Test", "000", "PATIENT");
            jdbc.update("INSERT INTO user_account (username,password_hash,full_name,phone,role) VALUES (?,?,?,?,?)",
                    username, "hash", "Test", "000", "PATIENT");
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE username=?", Long.class, username));

        assertThrows(ExpectedFailure.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO user_account (username,password_hash,full_name,phone,role) VALUES (?,?,?,?,?)",
                    username, "hash", "Test", "000", "PATIENT");
            jdbc.update("INSERT INTO specialty (code,name) VALUES (?,?)", username, "Test");
            throw new ExpectedFailure();
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE username=?", Long.class, username));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM specialty WHERE code=?", Long.class, username));
    }

    @Test
    void oneAccountCannotOwnTwoPatientProfiles() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "n00_user_" + suffix;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(DataIntegrityViolationException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO user_account (username,password_hash,full_name,phone,role) VALUES (?,?,?,?,?)",
                    username, "hash", "Test", "000", "PATIENT");
            Long userId = jdbc.queryForObject("SELECT id FROM user_account WHERE username=?", Long.class, username);
            jdbc.update("INSERT INTO patient (patient_code,user_id,full_name,date_of_birth,gender) VALUES (?,?,?,?,?)",
                    "P1_" + suffix, userId, "Test", "2000-01-01", "OTHER");
            jdbc.update("INSERT INTO patient (patient_code,user_id,full_name,date_of_birth,gender) VALUES (?,?,?,?,?)",
                    "P2_" + suffix, userId, "Test", "2000-01-01", "OTHER");
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE username=?", Long.class, username));
    }

    @Test
    void oneAppointmentCannotCreateTwoVisits() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String patientUsername = "n00_patient_" + suffix;
        String doctorUsername = "n00_doctor_" + suffix;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(DataIntegrityViolationException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO user_account (username,password_hash,full_name,phone,role) VALUES (?,?,?,?,?)",
                    patientUsername, "hash", "Patient", "000", "PATIENT");
            jdbc.update("INSERT INTO user_account (username,password_hash,full_name,phone,role) VALUES (?,?,?,?,?)",
                    doctorUsername, "hash", "Doctor", "000", "DOCTOR");
            Long patientUserId = jdbc.queryForObject("SELECT id FROM user_account WHERE username=?", Long.class, patientUsername);
            Long doctorUserId = jdbc.queryForObject("SELECT id FROM user_account WHERE username=?", Long.class, doctorUsername);
            jdbc.update("INSERT INTO patient (patient_code,user_id,full_name,date_of_birth,gender) VALUES (?,?,?,?,?)",
                    "P_" + suffix, patientUserId, "Patient", "2000-01-01", "OTHER");
            Long patientId = jdbc.queryForObject("SELECT id FROM patient WHERE patient_code=?", Long.class, "P_" + suffix);
            jdbc.update("INSERT INTO specialty (code,name) VALUES (?,?)", "S_" + suffix, "Test");
            Long specialtyId = jdbc.queryForObject("SELECT id FROM specialty WHERE code=?", Long.class, "S_" + suffix);
            jdbc.update("INSERT INTO clinic_room (specialty_id,code,name) VALUES (?,?,?)",
                    specialtyId, "R_" + suffix, "Test");
            Long roomId = jdbc.queryForObject("SELECT id FROM clinic_room WHERE code=?", Long.class, "R_" + suffix);
            jdbc.update("INSERT INTO doctor_profile (user_id,specialty_id) VALUES (?,?)", doctorUserId, specialtyId);
            jdbc.update("INSERT INTO clinic_slot (room_id,doctor_id,start_at,end_at,capacity,status) " +
                    "VALUES (?,?,?,?,?,?)", roomId, doctorUserId, "2026-11-08 01:00:00",
                    "2026-11-08 01:15:00", 2, "OPEN");
            Long slotId = jdbc.queryForObject("SELECT id FROM clinic_slot WHERE room_id=?", Long.class, roomId);
            jdbc.update("INSERT INTO appointment (appointment_code,patient_id,slot_id,reason,status) VALUES (?,?,?,?,?)",
                    "A_" + suffix, patientId, slotId, "Test", "PENDING");
            Long appointmentId = jdbc.queryForObject("SELECT id FROM appointment WHERE appointment_code=?",
                    Long.class, "A_" + suffix);
            jdbc.update("INSERT INTO medical_visit (visit_code,patient_id,appointment_id,slot_id,room_id,queue_date," +
                    "queue_number,arrived_at,status) VALUES (?,?,?,?,?,?,?,?,?)",
                    "V1_" + suffix, patientId, appointmentId, slotId, roomId, "2026-11-08", 1,
                    "2026-11-08 01:00:00", "WAITING");
            jdbc.update("INSERT INTO medical_visit (visit_code,patient_id,appointment_id,slot_id,room_id,queue_date," +
                    "queue_number,arrived_at,status) VALUES (?,?,?,?,?,?,?,?,?)",
                    "V2_" + suffix, patientId, appointmentId, slotId, roomId, "2026-11-08", 2,
                    "2026-11-08 01:00:00", "WAITING");
        }));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE username IN (?,?)",
                Long.class, patientUsername, doctorUsername));
    }

    @Test
    void jpaAuditRecordsActorAndTimestamps() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String firstUser = "n00_audit1_" + suffix;
        String secondUser = "n00_audit2_" + suffix;
        String code = "AUD_" + suffix;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO user_account (username,password_hash,full_name,phone,role) VALUES (?,?,?,?,?)",
                        firstUser, "hash", "First", "000", "ADMIN");
                jdbc.update("INSERT INTO user_account (username,password_hash,full_name,phone,role) VALUES (?,?,?,?,?)",
                        secondUser, "hash", "Second", "000", "ADMIN");
                Long firstId = jdbc.queryForObject("SELECT id FROM user_account WHERE username=?", Long.class, firstUser);
                Long secondId = jdbc.queryForObject("SELECT id FROM user_account WHERE username=?", Long.class, secondUser);
                authenticateAs(firstId);
                AuditProbeSpecialty specialty = new AuditProbeSpecialty(code, "Before");
                entityManager.persist(specialty);
                entityManager.flush();
                assertEquals(firstId, jdbc.queryForObject("SELECT created_by FROM specialty WHERE id=?",
                        Long.class, specialty.getId()));
                assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM specialty WHERE id=? " +
                        "AND created_at IS NOT NULL AND updated_at IS NOT NULL", Long.class, specialty.getId()));
                authenticateAs(secondId);
                specialty.rename("After");
                entityManager.flush();
                assertEquals(secondId, jdbc.queryForObject("SELECT updated_by FROM specialty WHERE id=?",
                        Long.class, specialty.getId()));
                status.setRollbackOnly();
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM specialty WHERE code=?", Long.class, code));
    }

    private static void authenticateAs(Long id) {
        AuthenticatedActor actor = () -> id;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, java.util.List.of()));
    }

    static class ExpectedFailure extends RuntimeException { }
}
