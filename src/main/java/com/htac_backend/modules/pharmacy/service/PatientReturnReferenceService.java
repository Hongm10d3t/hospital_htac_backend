package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.shared.api.BusinessException;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M06-owned source and posted-return queries; callers hold the prescription lock. */
@Service
public class PatientReturnReferenceService {
    private final JdbcTemplate jdbc;

    public PatientReturnReferenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public SourceLine requirePostedSource(Long sourceItemId, Long prescriptionId,
            Long prescriptionItemId, Long batchId) {
        var rows = jdbc.query("""
                SELECT i.id, i.batch_id, i.prescription_item_id, i.quantity, d.prescription_id
                FROM stock_document_item i JOIN stock_document d ON d.id = i.document_id
                WHERE i.id = ? AND d.type = 'DISPENSE' AND d.status = 'POSTED'
                """, (rs, row) -> new SourceLine(rs.getLong("id"), rs.getLong("batch_id"),
                rs.getLong("prescription_item_id"), rs.getBigDecimal("quantity"),
                rs.getLong("prescription_id")), sourceItemId);
        if (rows.size() != 1) {
            throw new BusinessException("PATIENT_RETURN_SOURCE_INVALID",
                    "Dòng cấp gốc chưa ghi sổ hoặc không tồn tại");
        }
        SourceLine source = rows.getFirst();
        if (!source.prescriptionId().equals(prescriptionId)
                || !source.prescriptionItemId().equals(prescriptionItemId)
                || !source.batchId().equals(batchId)) {
            throw new BusinessException("PATIENT_RETURN_SOURCE_MISMATCH",
                    "Dòng cấp gốc không khớp đơn, dòng đơn hoặc lô");
        }
        return source;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public BigDecimal postedReturned(Long sourceItemId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(i.quantity), 0)
                FROM stock_document_item i JOIN stock_document d ON d.id = i.document_id
                WHERE i.source_item_id = ? AND d.type = 'PATIENT_RETURN' AND d.status = 'POSTED'
                """, BigDecimal.class, sourceItemId);
    }

    public record SourceLine(Long id, Long batchId, Long prescriptionItemId,
            BigDecimal quantity, Long prescriptionId) { }
}
