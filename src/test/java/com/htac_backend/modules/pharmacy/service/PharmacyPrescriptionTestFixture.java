package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Isolated MySQL data for M06 prescription and ledger integration tests. */
final class PharmacyPrescriptionTestFixture implements AutoCloseable {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    final JdbcTemplate jdbc;
    final UserAccountRepository accounts;
    final String tag = UUID.randomUUID().toString().substring(0, 12);
    final UserAccount doctor;
    final UserAccount pharmacist;
    final long specialtyId;
    final long roomId;
    final long slotId;
    final long patientId;
    final long visitId;

    PharmacyPrescriptionTestFixture(JdbcTemplate jdbc, UserAccountRepository accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        jdbc.update("INSERT INTO specialty (code, name) VALUES (?, 'Synthetic specialty')", "s" + tag);
        specialtyId = id("SELECT id FROM specialty WHERE code = ?", "s" + tag);
        jdbc.update("INSERT INTO clinic_room (specialty_id, code, name) VALUES (?, ?, ?)",
                specialtyId, "r" + tag, "Synthetic room");
        roomId = id("SELECT id FROM clinic_room WHERE code = ?", "r" + tag);
        doctor = accounts.saveAndFlush(new UserAccount("d" + tag, "{noop}unused",
                "Synthetic doctor", "0000000000", AccountRole.DOCTOR));
        pharmacist = accounts.saveAndFlush(new UserAccount("p" + tag, "{noop}unused",
                "Synthetic pharmacist", "0000000000", AccountRole.PHARMACIST));
        jdbc.update("INSERT INTO doctor_profile (user_id, specialty_id) VALUES (?, ?)",
                doctor.getId(), specialtyId);
        LocalDate today = LocalDate.now(ZONE);
        Instant start = today.atTime(8, 0).atZone(ZONE).toInstant();
        jdbc.update("INSERT INTO clinic_slot (room_id, doctor_id, start_at, end_at, capacity) "
                + "VALUES (?, ?, ?, ?, 10)", roomId, doctor.getId(), Timestamp.from(start),
                Timestamp.from(start.plusSeconds(3600)));
        slotId = id("SELECT id FROM clinic_slot WHERE room_id = ? AND start_at = ?",
                roomId, Timestamp.from(start));
        jdbc.update("INSERT INTO patient (patient_code, full_name, date_of_birth, gender) "
                + "VALUES (?, 'Synthetic patient', ?, 'OTHER')", "p" + tag, LocalDate.of(1990, 1, 1));
        patientId = id("SELECT id FROM patient WHERE patient_code = ?", "p" + tag);
        jdbc.update("INSERT INTO medical_visit (visit_code, patient_id, slot_id, room_id, "
                + "queue_date, queue_number, arrived_at, status, diagnosis) "
                + "VALUES (?, ?, ?, ?, ?, 1, ?, 'IN_PROGRESS', 'Synthetic diagnosis')",
                "v" + tag, patientId, slotId, roomId, today, Timestamp.from(start.plusSeconds(300)));
        visitId = id("SELECT id FROM medical_visit WHERE visit_code = ?", "v" + tag);
    }

    long medicine(String suffix) {
        String code = "m" + suffix + tag;
        jdbc.update("INSERT INTO medicine (code, name, active_ingredient, strength, dosage_form, "
                + "base_unit) VALUES (?, ?, 'Synthetic ingredient', '1 mg', 'tablet', 'tablet')",
                code, "Synthetic medicine " + suffix);
        return id("SELECT id FROM medicine WHERE code = ?", code);
    }

    long batch(long medicineId, String suffix, LocalDate expiry) {
        jdbc.update("INSERT INTO medicine_batch (medicine_id, batch_number, expiry_date) "
                + "VALUES (?, ?, ?)", medicineId, "b" + suffix + tag, expiry);
        return id("SELECT id FROM medicine_batch WHERE medicine_id = ? AND batch_number = ?",
                medicineId, "b" + suffix + tag);
    }

    long prescription(long medicineId, BigDecimal quantity, String status) {
        String code = "RX" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Instant issued = Instant.now().minusSeconds(60);
        jdbc.update("INSERT INTO prescription (prescription_code, visit_id, doctor_id, "
                + "issued_at, status) VALUES (?, ?, ?, ?, ?)", code, visitId, doctor.getId(),
                "ISSUED".equals(status) ? Timestamp.from(issued) : null, status);
        long prescriptionId = id("SELECT id FROM prescription WHERE prescription_code = ?", code);
        jdbc.update("INSERT INTO prescription_item (prescription_id, medicine_id, quantity, "
                + "usage_instruction) VALUES (?, ?, ?, 'Synthetic instruction')",
                prescriptionId, medicineId, quantity);
        return prescriptionId;
    }

    long prescriptionItem(long prescriptionId) {
        return id("SELECT id FROM prescription_item WHERE prescription_id = ?", prescriptionId);
    }

    long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    @Override
    public void close() {
        SecurityContextHolder.clearContext();
        List<Long> documentIds = jdbc.queryForList("""
                SELECT DISTINCT d.id FROM stock_document d
                LEFT JOIN stock_document_item i ON i.document_id = d.id
                LEFT JOIN medicine_batch b ON b.id = i.batch_id
                LEFT JOIN medicine m ON m.id = b.medicine_id
                WHERE d.prescription_id IN (SELECT id FROM prescription WHERE visit_id = ?)
                    OR m.code LIKE ?
                """, Long.class, visitId, "%" + tag);
        for (Long id : documentIds) {
            jdbc.update("DELETE FROM stock_document_item WHERE document_id = ? AND source_item_id IS NOT NULL", id);
        }
        for (Long id : documentIds) {
            jdbc.update("DELETE FROM stock_document_item WHERE document_id = ?", id);
            jdbc.update("DELETE FROM stock_document WHERE id = ?", id);
        }
        jdbc.update("DELETE FROM prescription_item WHERE prescription_id IN "
                + "(SELECT id FROM prescription WHERE visit_id = ?)", visitId);
        jdbc.update("DELETE FROM prescription WHERE visit_id = ?", visitId);
        jdbc.update("DELETE FROM medical_visit WHERE id = ?", visitId);
        jdbc.update("DELETE FROM patient WHERE id = ?", patientId);
        jdbc.update("DELETE FROM clinic_slot WHERE id = ?", slotId);
        jdbc.update("DELETE FROM doctor_profile WHERE user_id = ?", doctor.getId());
        jdbc.update("DELETE FROM medicine_batch WHERE batch_number LIKE ?", "%" + tag);
        jdbc.update("DELETE FROM medicine WHERE code LIKE ?", "%" + tag);
        jdbc.update("DELETE FROM clinic_room WHERE id = ?", roomId);
        jdbc.update("DELETE FROM specialty WHERE id = ?", specialtyId);
        accounts.deleteById(doctor.getId());
        accounts.deleteById(pharmacist.getId());
    }
}
