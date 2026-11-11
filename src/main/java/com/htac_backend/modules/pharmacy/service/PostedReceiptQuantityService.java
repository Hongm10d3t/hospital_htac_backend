package com.htac_backend.modules.pharmacy.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M06-owned read contract: only posted RECEIPT documents count as received. */
@Service
public class PostedReceiptQuantityService {
    private final JdbcTemplate jdbc;

    public PostedReceiptQuantityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Map<Long, BigDecimal> receivedByMedicine(Long requestId) {
        Map<Long, BigDecimal> result = new HashMap<>();
        jdbc.query("SELECT b.medicine_id, SUM(i.quantity) FROM stock_document d "
                + "JOIN stock_document_item i ON i.document_id = d.id "
                + "JOIN medicine_batch b ON b.id = i.batch_id "
                + "WHERE d.purchase_request_id = ? AND d.type = 'RECEIPT' "
                + "AND d.status = 'POSTED' GROUP BY b.medicine_id",
                (RowCallbackHandler) rs -> result.put(rs.getLong(1), rs.getBigDecimal(2)), requestId);
        return result;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean hasDrafts(Long requestId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM stock_document WHERE purchase_request_id = ? "
                + "AND type = 'RECEIPT' AND status = 'DRAFT')", Boolean.class, requestId));
    }
}
