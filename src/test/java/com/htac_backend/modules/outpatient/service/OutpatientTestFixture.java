package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Synthetic MySQL fixture shared by M05 integration tests. */
final class OutpatientTestFixture implements AutoCloseable {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    final JdbcTemplate jdbc;
    final UserAccountRepository accounts;
    final String tag;
    final long specialtyId;
    final long roomA;
    final long roomB;
    final UserAccount doctorA;
    final UserAccount doctorB;
    final UserAccount receptionist;
    final UserAccount pharmacist;
    final long slotA;
    final long slotB;
    final long historicSlot;
    final long patientA;
    final long patientB;
    final long visitA;
    final long visitB;
    final long historicVisit;
    final long appointmentA;

    private OutpatientTestFixture(JdbcTemplate jdbc, UserAccountRepository accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.tag = UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO specialty (code, name) VALUES (?, 'Synthetic specialty')", "s" + tag);
        specialtyId = id("SELECT id FROM specialty WHERE code = ?", "s" + tag);
        roomA = room("a");
        roomB = room("b");
        doctorA = accounts.saveAndFlush(new UserAccount("a" + tag, "{noop}unused",
                "Synthetic doctor A", "0000000000", AccountRole.DOCTOR));
        doctorB = accounts.saveAndFlush(new UserAccount("b" + tag, "{noop}unused",
                "Synthetic doctor B", "0000000000", AccountRole.DOCTOR));
        receptionist = accounts.saveAndFlush(new UserAccount("e" + tag, "{noop}unused",
                "Synthetic receptionist", "0000000000", AccountRole.RECEPTIONIST));
        pharmacist = accounts.saveAndFlush(new UserAccount("f" + tag, "{noop}unused",
                "Synthetic pharmacist", "0000000000", AccountRole.PHARMACIST));
        jdbc.update("INSERT INTO doctor_profile (user_id, specialty_id) VALUES (?, ?), (?, ?)",
                doctorA.getId(), specialtyId, doctorB.getId(), specialtyId);
        LocalDate today = LocalDate.now(ZONE);
        slotA = slot(roomA, doctorA.getId(), today);
        slotB = slot(roomB, doctorB.getId(), today);
        historicSlot = slot(roomA, doctorA.getId(), today.minusDays(1));
        patientA = patient("a");
        patientB = patient("b");
        jdbc.update("INSERT INTO appointment (appointment_code, patient_id, slot_id, reason) "
                + "VALUES (?, ?, ?, 'Synthetic reason')", "A" + tag, patientA, slotA);
        appointmentA = id("SELECT id FROM appointment WHERE appointment_code = ?", "A" + tag);
        visitA = visit("a", patientA, appointmentA, slotA, roomA, today, 1, "WAITING", null);
        visitB = visit("b", patientB, null, slotB, roomB, today, 1, "WAITING", null);
        historicVisit = visit("h", patientA, null, historicSlot, roomA, today.minusDays(1),
                1, "COMPLETED", "Historical diagnosis");
    }

    static OutpatientTestFixture create(JdbcTemplate jdbc, UserAccountRepository accounts) {
        return new OutpatientTestFixture(jdbc, accounts);
    }

    void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    long id(String sql, Object argument) {
        return jdbc.queryForObject(sql, Long.class, argument);
    }

    long medicine(String suffix, boolean active) {
        String code = "m" + suffix + tag;
        jdbc.update("INSERT INTO medicine (code, name, active_ingredient, strength, dosage_form, "
                + "base_unit, is_active) VALUES (?, ?, 'Synthetic ingredient', '1 mg', 'tablet', 'tablet', ?)",
                code, "Synthetic medicine " + suffix, active);
        return id("SELECT id FROM medicine WHERE code = ?", code);
    }

    private long room(String suffix) {
        jdbc.update("INSERT INTO clinic_room (specialty_id, code, name) VALUES (?, ?, ?)",
                specialtyId, "r" + suffix + tag, "Synthetic room " + suffix);
        return id("SELECT id FROM clinic_room WHERE code = ?", "r" + suffix + tag);
    }

    private long slot(long roomId, long doctorId, LocalDate date) {
        Instant start = date.atTime(8, 0).atZone(ZONE).toInstant();
        jdbc.update("INSERT INTO clinic_slot (room_id, doctor_id, start_at, end_at, capacity) "
                + "VALUES (?, ?, ?, ?, 10)", roomId, doctorId, Timestamp.from(start),
                Timestamp.from(start.plusSeconds(3600)));
        return jdbc.queryForObject("SELECT id FROM clinic_slot WHERE room_id = ? AND start_at = ?",
                Long.class, roomId, Timestamp.from(start));
    }

    private long patient(String suffix) {
        jdbc.update("INSERT INTO patient (patient_code, full_name, date_of_birth, gender) "
                + "VALUES (?, ?, ?, 'OTHER')", "p" + suffix + tag,
                "Synthetic patient " + suffix, LocalDate.of(1990, 1, 1));
        return id("SELECT id FROM patient WHERE patient_code = ?", "p" + suffix + tag);
    }

    private long visit(String suffix, long patientId, Long appointmentId, long slotId, long roomId,
            LocalDate date, int queue, String status, String diagnosis) {
        Instant arrived = date.atTime(8, 5).atZone(ZONE).toInstant();
        jdbc.update("INSERT INTO medical_visit (visit_code, patient_id, appointment_id, slot_id, "
                + "room_id, queue_date, queue_number, arrived_at, status, diagnosis) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", "v" + suffix + tag, patientId,
                appointmentId, slotId, roomId, date, queue, Timestamp.from(arrived), status, diagnosis);
        return id("SELECT id FROM medical_visit WHERE visit_code = ?", "v" + suffix + tag);
    }

    @Override
    public void close() {
        SecurityContextHolder.clearContext();
        List<Long> prescriptionIds = jdbc.queryForList("SELECT id FROM prescription WHERE visit_id IN "
                + "(SELECT id FROM medical_visit WHERE slot_id IN (?, ?, ?))", Long.class,
                slotA, slotB, historicSlot);
        for (Long prescriptionId : prescriptionIds) {
            jdbc.update("DELETE FROM stock_document_item WHERE document_id IN "
                    + "(SELECT id FROM stock_document WHERE prescription_id = ?)", prescriptionId);
            jdbc.update("DELETE FROM stock_document WHERE prescription_id = ?", prescriptionId);
            jdbc.update("DELETE FROM prescription_item WHERE prescription_id = ?", prescriptionId);
            jdbc.update("DELETE FROM prescription WHERE id = ?", prescriptionId);
        }
        jdbc.update("DELETE FROM medical_visit WHERE slot_id IN (?, ?, ?)", slotA, slotB, historicSlot);
        jdbc.update("DELETE FROM appointment WHERE id = ?", appointmentA);
        jdbc.update("DELETE FROM patient WHERE id IN (?, ?)", patientA, patientB);
        jdbc.update("DELETE FROM clinic_slot WHERE id IN (?, ?, ?)", slotA, slotB, historicSlot);
        jdbc.update("DELETE FROM doctor_profile WHERE user_id IN (?, ?)", doctorA.getId(), doctorB.getId());
        jdbc.update("DELETE FROM medicine WHERE code IN (?, ?, ?)", "m1" + tag, "m2" + tag,
                "m3" + tag);
        jdbc.update("DELETE FROM clinic_room WHERE id IN (?, ?)", roomA, roomB);
        jdbc.update("DELETE FROM specialty WHERE id = ?", specialtyId);
        accounts.deleteById(doctorA.getId());
        accounts.deleteById(doctorB.getId());
        accounts.deleteById(receptionist.getId());
        accounts.deleteById(pharmacist.getId());
    }
}
