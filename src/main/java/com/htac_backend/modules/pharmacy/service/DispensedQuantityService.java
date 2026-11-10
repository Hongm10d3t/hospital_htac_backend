package com.htac_backend.modules.pharmacy.service;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Caller holds the prescription row lock when the result is used for a write. */
@Service
public class DispensedQuantityService {
    private final JdbcTemplate jdbc;

    public DispensedQuantityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public BigDecimal postedQuantity(Long prescriptionItemId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(i.quantity), 0)
                FROM stock_document_item i JOIN stock_document d ON d.id = i.document_id
                WHERE d.type = 'DISPENSE' AND d.status = 'POSTED' AND i.prescription_item_id = ?
                """, BigDecimal.class, prescriptionItemId);
    }
}
