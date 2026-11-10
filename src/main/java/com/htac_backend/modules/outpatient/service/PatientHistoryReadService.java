package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.outpatient.dto.PatientPrescriptionDetail;
import com.htac_backend.modules.outpatient.dto.PatientPrescriptionItem;
import com.htac_backend.modules.outpatient.dto.PatientPrescriptionSummary;
import com.htac_backend.modules.outpatient.dto.PatientVisitDetail;
import com.htac_backend.modules.outpatient.dto.PatientVisitSummary;
import com.htac_backend.modules.outpatient.entity.MedicalVisit;
import com.htac_backend.modules.outpatient.entity.VisitStatus;
import com.htac_backend.modules.outpatient.repository.MedicalVisitRepository;
import com.htac_backend.modules.pharmacy.service.MedicineDisplayLookupService;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M05-owned read contract. Caller resolves the signed-in account to its patient first. */
@Service
public class PatientHistoryReadService {
    private static final String VISIBLE_PRESCRIPTION = "(p.status = 'ISSUED' "
            + "OR (p.status = 'CANCELLED' AND p.issued_at IS NOT NULL))";
    private final MedicalVisitRepository visits;
    private final JdbcTemplate jdbc;
    private final MedicineDisplayLookupService medicines;

    public PatientHistoryReadService(MedicalVisitRepository visits, JdbcTemplate jdbc,
            MedicineDisplayLookupService medicines) {
        this.visits = visits;
        this.jdbc = jdbc;
        this.medicines = medicines;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public PageResponse<PatientVisitSummary> visits(Long patientId, LocalDate from, LocalDate to,
            PageQuery page) {
        return PageResponse.from(visits.history(patientId, from, to, page.toPageRequest())
                .map(PatientVisitSummary::from));
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public PatientVisitDetail visit(Long patientId, Long visitId) {
        MedicalVisit visit = visits.findByIdAndPatientIdAndStatusNot(visitId, patientId,
                VisitStatus.VOIDED).orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
        boolean completed = visit.getStatus() == VisitStatus.COMPLETED;
        return new PatientVisitDetail(PatientVisitSummary.from(visit),
                completed ? visit.getDiagnosisCode() : null,
                completed ? visit.getClinicalNotes() : null,
                completed ? visit.getTreatmentPlan() : null,
                completed ? visit.getFollowUpDate() : null,
                prescriptionsForVisit(patientId, visitId));
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public PageResponse<PatientPrescriptionSummary> prescriptions(Long patientId, LocalDate from,
            LocalDate to, PageQuery page) {
        String predicate = " FROM prescription p JOIN medical_visit v ON v.id = p.visit_id "
                + "WHERE v.patient_id = ? AND v.status <> 'VOIDED' AND " + VISIBLE_PRESCRIPTION
                + " AND (? IS NULL OR v.queue_date >= ?) AND (? IS NULL OR v.queue_date <= ?)";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + predicate, Long.class,
                patientId, from, from, to, to);
        if (page.page() > 0 && (long) page.page() * page.size() >= total) {
            throw new InvalidRequestException("Trang yêu cầu vượt phạm vi");
        }
        String sortColumn = "id".equals(page.sortBy()) ? "p.id" : "p.issued_at";
        List<PatientPrescriptionSummary> items = jdbc.query("SELECT p.id, p.prescription_code, p.visit_id, "
                        + "p.status, p.issued_at, p.note" + predicate + " ORDER BY " + sortColumn + " "
                        + page.direction().name() + ", p.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> summary(rs.getLong("id"), rs.getString("prescription_code"),
                        rs.getLong("visit_id"), rs.getString("status"), rs.getTimestamp("issued_at"),
                        rs.getString("note")), patientId, from, from, to, to,
                page.size(), (long) page.page() * page.size());
        return new PageResponse<>(items, page.page(), page.size(), total,
                (int) ((total + page.size() - 1) / page.size()));
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public PatientPrescriptionDetail prescription(Long patientId, Long prescriptionId) {
        PatientPrescriptionSummary prescription = jdbc.query("SELECT p.id, p.prescription_code, "
                        + "p.visit_id, p.status, p.issued_at, p.note FROM prescription p "
                        + "JOIN medical_visit v ON v.id = p.visit_id WHERE p.id = ? AND v.patient_id = ? "
                        + "AND v.status <> 'VOIDED' AND " + VISIBLE_PRESCRIPTION,
                (rs, row) -> summary(rs.getLong("id"), rs.getString("prescription_code"),
                        rs.getLong("visit_id"), rs.getString("status"), rs.getTimestamp("issued_at"),
                        rs.getString("note")), prescriptionId, patientId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn thuốc"));
        record Item(Long medicineId, BigDecimal quantity, String instruction) { }
        List<Item> raw = jdbc.query("SELECT medicine_id, quantity, usage_instruction FROM prescription_item "
                        + "WHERE prescription_id = ? ORDER BY id",
                (rs, row) -> new Item(rs.getLong("medicine_id"), rs.getBigDecimal("quantity"),
                        rs.getString("usage_instruction")), prescriptionId);
        var labels = medicines.labels(raw.stream().map(Item::medicineId).toList());
        return new PatientPrescriptionDetail(prescription, raw.stream().map(item -> {
            var label = labels.get(item.medicineId());
            return new PatientPrescriptionItem(item.medicineId(), label.name(), label.baseUnit(),
                    item.quantity(), item.instruction());
        }).toList());
    }

    private List<PatientPrescriptionSummary> prescriptionsForVisit(Long patientId, Long visitId) {
        return jdbc.query("SELECT p.id, p.prescription_code, p.visit_id, p.status, p.issued_at, p.note "
                        + "FROM prescription p JOIN medical_visit v ON v.id = p.visit_id "
                        + "WHERE v.patient_id = ? AND p.visit_id = ? AND " + VISIBLE_PRESCRIPTION
                        + " ORDER BY p.issued_at DESC, p.id DESC",
                (rs, row) -> summary(rs.getLong("id"), rs.getString("prescription_code"),
                        rs.getLong("visit_id"), rs.getString("status"), rs.getTimestamp("issued_at"),
                        rs.getString("note")), patientId, visitId);
    }

    private PatientPrescriptionSummary summary(Long id, String code, Long visitId, String status,
            Timestamp issuedAt, String note) {
        return new PatientPrescriptionSummary(id, code, visitId, status,
                issuedAt == null ? null : issuedAt.toInstant(), note);
    }
}
