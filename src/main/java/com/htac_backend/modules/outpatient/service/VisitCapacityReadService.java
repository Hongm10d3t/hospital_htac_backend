package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.outpatient.dto.VisitOccupancy;
import java.util.HashSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M05-owned read contract for shared slot capacity. */
@Service
public class VisitCapacityReadService {
    private final JdbcTemplate jdbc;

    public VisitCapacityReadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public VisitOccupancy occupancy(Long slotId) {
        var received = new HashSet<Long>();
        var rows = jdbc.query("SELECT appointment_id FROM medical_visit WHERE slot_id = ? "
                + "AND status <> 'VOIDED'", (result, index) -> {
                    long appointmentId = result.getLong(1);
                    return result.wasNull() ? null : appointmentId;
                }, slotId);
        for (Long appointmentId : rows) {
            if (appointmentId != null) {
                received.add(appointmentId);
            }
        }
        return new VisitOccupancy(rows.size(), received);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean hasAnyVisit(Long slotId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM medical_visit WHERE slot_id = ?)", Boolean.class, slotId));
    }
}
