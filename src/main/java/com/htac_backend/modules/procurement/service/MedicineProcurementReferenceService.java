package com.htac_backend.modules.procurement.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M07-owned read contract used before changing a medicine's identity. */
@Service
public class MedicineProcurementReferenceService {
    private final JdbcTemplate jdbc;

    public MedicineProcurementReferenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean hasPurchaseRequestLines(Long medicineId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM purchase_request_item WHERE medicine_id = ?)",
                Boolean.class, medicineId));
    }
}
