package com.htac_backend.modules.pharmacy.service;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Derives balances exclusively from POSTED documents; callers lock each batch before stock writes. */
@Service
public class StockBalanceService {
    private static final String BALANCE_SQL = """
            SELECT COALESCE(SUM(
                CASE WHEN d.type = 'TRANSFER' AND d.destination_location = ?
                    AND d.destination_bucket = ? THEN i.quantity ELSE 0 END
                + CASE WHEN d.stock_location = ? AND d.stock_bucket = ? THEN
                    CASE WHEN d.type IN ('OPENING','RECEIPT','PATIENT_RETURN','ADJUSTMENT')
                        THEN i.quantity
                    WHEN d.type IN ('TRANSFER','DISPENSE','SUPPLIER_RETURN','DISPOSAL')
                        THEN -i.quantity ELSE 0 END
                    ELSE 0 END), 0)
            FROM stock_document_item i JOIN stock_document d ON d.id = i.document_id
            WHERE d.status = 'POSTED' AND i.batch_id = ?
            """;

    private final JdbcTemplate jdbc;

    public StockBalanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public BigDecimal balance(StockPosition position) {
        return jdbc.queryForObject(BALANCE_SQL, BigDecimal.class,
                position.location(), position.bucket(), position.location(), position.bucket(),
                position.batchId());
    }
}
