package com.htac_backend.modules.pharmacy.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M06-owned read contract used while M05 holds the prescription row lock. */
@Service
public class PrescriptionStockReferenceService {
    private final JdbcTemplate jdbc;

    public PrescriptionStockReferenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean hasActiveDocument(Long prescriptionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM stock_document WHERE prescription_id = ? "
                        + "AND status <> 'CANCELLED')", Boolean.class, prescriptionId));
    }
}
