package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.outpatient.dto.VisitOutcome;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M05-owned read contract for appointment finalization. */
@Service
public class VisitOutcomeReadService {
    private final JdbcTemplate jdbc;

    public VisitOutcomeReadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Optional<VisitOutcome> nonVoidedForAppointment(Long appointmentId) {
        return jdbc.query("SELECT patient_id, slot_id, status, outcome_reason FROM medical_visit "
                        + "WHERE appointment_id = ? AND status <> 'VOIDED'",
                (rs, row) -> new VisitOutcome(rs.getLong("patient_id"), rs.getLong("slot_id"),
                        rs.getString("status"), rs.getString("outcome_reason")), appointmentId)
                .stream().findFirst();
    }
}
