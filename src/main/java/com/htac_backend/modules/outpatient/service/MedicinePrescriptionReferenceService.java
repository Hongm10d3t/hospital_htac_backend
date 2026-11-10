package com.htac_backend.modules.outpatient.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M05-owned read contract used before changing a medicine's identity. */
@Service
public class MedicinePrescriptionReferenceService {
    private final JdbcTemplate jdbc;

    public MedicinePrescriptionReferenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean hasPrescriptionLines(Long medicineId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM prescription_item WHERE medicine_id = ?)",
                Boolean.class, medicineId));
    }
}
