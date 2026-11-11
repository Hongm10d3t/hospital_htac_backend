package com.htac_backend.modules.reporting.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.reporting.dto.DispenseReportLine;
import com.htac_backend.modules.reporting.dto.PrescriptionMedicineReportRow;
import com.htac_backend.modules.reporting.dto.PrescriptionReportDetail;
import com.htac_backend.modules.reporting.dto.PrescriptionReportItem;
import com.htac_backend.modules.reporting.dto.ReportPage;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionReportService {
    private final ReportQuerySupport query;
    private final AccountRoleLookupService accounts;

    public PrescriptionReportService(ReportQuerySupport query, AccountRoleLookupService accounts) {
        this.query = query;
        this.accounts = accounts;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
    @Transactional(readOnly = true)
    public ReportPage<PrescriptionMedicineReportRow> medicines(LocalDate from, LocalDate to,
            Long medicineId, PageQuery page) {
        requireDrugReader();
        validId(medicineId);
        ReportWindow window = ReportWindow.of(from, to);
        var args = new MapSqlParameterSource("start", window.startSql())
                .addValue("end", window.endSql()).addValue("medicineId", medicineId);
        String sql = """
                SELECT m.id AS medicine_id, m.code, m.name, m.base_unit,
                    SUM(event.prescribed) AS prescribed_quantity,
                    SUM(event.dispensed) AS dispensed_quantity,
                    SUM(event.unfilled) AS current_unfilled_quantity,
                    SUM(event.unchecked) AS unchecked_line_count
                FROM (
                    SELECT pi.medicine_id, pi.quantity AS prescribed,
                        CAST(0 AS DECIMAL(14,3)) AS dispensed,
                        CASE WHEN p.dispensing_checked_at IS NOT NULL THEN pi.unfilled_quantity
                            ELSE 0 END AS unfilled,
                        CASE WHEN p.dispensing_checked_at IS NULL THEN 1 ELSE 0 END AS unchecked
                    FROM prescription p JOIN prescription_item pi ON pi.prescription_id = p.id
                        JOIN medical_visit v ON v.id = p.visit_id
                    WHERE p.status = 'ISSUED' AND v.status <> 'VOIDED'
                        AND p.issued_at >= :start AND p.issued_at < :end
                    UNION ALL
                    SELECT pi.medicine_id, CAST(0 AS DECIMAL(14,3)), i.quantity,
                        CAST(0 AS DECIMAL(14,3)), 0
                    FROM stock_document d JOIN stock_document_item i ON i.document_id = d.id
                        JOIN prescription_item pi ON pi.id = i.prescription_item_id
                    WHERE d.type = 'DISPENSE' AND d.status = 'POSTED'
                        AND d.occurred_at >= :start AND d.occurred_at < :end
                ) event JOIN medicine m ON m.id = event.medicine_id
                WHERE (:medicineId IS NULL OR m.id = :medicineId)
                GROUP BY m.id, m.code, m.name, m.base_unit
                """;
        var result = query.page(sql, args, medicineSort(page), "medicine_id", page,
                (rs, number) -> new PrescriptionMedicineReportRow(rs.getLong("medicine_id"),
                        rs.getString("code"), rs.getString("name"), rs.getString("base_unit"),
                        rs.getBigDecimal("prescribed_quantity"), rs.getBigDecimal("dispensed_quantity"),
                        rs.getBigDecimal("current_unfilled_quantity"),
                        rs.getLong("unchecked_line_count")));
        return ReportPage.of(from, to,
                "prescribed=prescription.issued_at; dispensed=stock_document.occurred_at; "
                + "unfilled=current snapshot of prescriptions issued in range",
                medicineId == null ? Map.of() : Map.of("medicineId", medicineId), result);
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
    @Transactional(readOnly = true)
    public PrescriptionReportDetail prescription(Long id) {
        requireDrugReader();
        validIdRequired(id);
        var args = new MapSqlParameterSource("id", id);
        var headers = query.jdbc().query("""
                SELECT id, prescription_code, issued_at, dispensing_checked_at
                FROM prescription WHERE id = :id AND status = 'ISSUED'
                """, args, (rs, number) -> new PrescriptionReportDetail(rs.getLong("id"),
                rs.getString("prescription_code"), rs.getTimestamp("issued_at").toInstant(),
                rs.getTimestamp("dispensing_checked_at") == null ? null
                        : rs.getTimestamp("dispensing_checked_at").toInstant(),
                rs.getTimestamp("dispensing_checked_at") != null, java.util.List.of()));
        if (headers.isEmpty()) throw new NotFoundException("Không tìm thấy đơn đã phát hành");
        var header = headers.getFirst();
        var items = query.jdbc().query("""
                SELECT pi.id, pi.medicine_id, pi.quantity, pi.unfilled_quantity,
                    pi.unfilled_reason, pi.unfilled_note,
                    COALESCE(disp.quantity, 0) AS dispensed_quantity
                FROM prescription_item pi LEFT JOIN (
                    SELECT i.prescription_item_id, SUM(i.quantity) AS quantity
                    FROM stock_document_item i JOIN stock_document d ON d.id = i.document_id
                    WHERE d.type = 'DISPENSE' AND d.status = 'POSTED'
                    GROUP BY i.prescription_item_id
                ) disp ON disp.prescription_item_id = pi.id
                WHERE pi.prescription_id = :id ORDER BY pi.id
                """, args, (rs, number) -> new PrescriptionReportItem(rs.getLong("id"),
                rs.getLong("medicine_id"), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("dispensed_quantity"), rs.getBigDecimal("unfilled_quantity"),
                rs.getString("unfilled_reason"), rs.getString("unfilled_note")));
        return new PrescriptionReportDetail(header.id(), header.prescriptionCode(),
                header.issuedAt(), header.dispensingCheckedAt(), header.reviewed(), items);
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIST')")
    @Transactional(readOnly = true)
    public PageResponse<DispenseReportLine> dispenseLines(Long id, PageQuery page) {
        requireDrugReader();
        validIdRequired(id);
        if (query.jdbc().queryForObject("SELECT COUNT(*) FROM prescription "
                + "WHERE id = :id AND status = 'ISSUED'",
                new MapSqlParameterSource("id", id), Long.class) == 0) {
            throw new NotFoundException("Không tìm thấy đơn đã phát hành");
        }
        var args = new MapSqlParameterSource("id", id);
        String sql = """
                SELECT d.id AS document_id, d.document_code, i.id AS item_id,
                    i.prescription_item_id, i.batch_id, d.occurred_at, i.quantity
                FROM stock_document d JOIN stock_document_item i ON i.document_id = d.id
                WHERE d.prescription_id = :id AND d.type = 'DISPENSE' AND d.status = 'POSTED'
                """;
        return query.page(sql, args, dispenseSort(page), "document_id, item_id", page,
                (rs, number) -> new DispenseReportLine(rs.getLong("document_id"),
                        rs.getString("document_code"), rs.getLong("item_id"),
                        rs.getLong("prescription_item_id"), rs.getLong("batch_id"),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getBigDecimal("quantity")));
    }

    private void requireDrugReader() {
        Long id = query.actorId();
        if (!accounts.hasActiveRole(id, AccountRole.ADMIN)
                && !accounts.hasActiveRole(id, AccountRole.PHARMACIST)) {
            throw new AccessDeniedException("Không có quyền xem báo cáo dược");
        }
    }

    private void validId(Long id) {
        if (id != null && id <= 0) throw new InvalidRequestException("ID lọc không hợp lệ");
    }

    private void validIdRequired(Long id) {
        if (id == null || id <= 0) throw new InvalidRequestException("ID không hợp lệ");
    }

    private String medicineSort(PageQuery page) {
        return switch (page.sortBy()) {
            case "medicineId" -> "medicine_id";
            case "prescribedQuantity" -> "prescribed_quantity";
            case "dispensedQuantity" -> "dispensed_quantity";
            default -> throw new InvalidRequestException("Trường sắp xếp không hợp lệ");
        };
    }

    private String dispenseSort(PageQuery page) {
        return switch (page.sortBy()) {
            case "occurredAt" -> "occurred_at";
            case "documentId" -> "document_id";
            case "itemId" -> "item_id";
            default -> throw new InvalidRequestException("Trường sắp xếp không hợp lệ");
        };
    }
}
