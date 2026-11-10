package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.outpatient.dto.ReceptionVisit;
import com.htac_backend.modules.outpatient.entity.MedicalVisit;
import com.htac_backend.modules.outpatient.entity.VisitStatus;
import com.htac_backend.modules.outpatient.repository.MedicalVisitRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M05-owned status changes exposed to the M04 reception coordinator. */
@Service
public class VisitReceptionOutcomeService {
    private final MedicalVisitRepository visits;
    private final JdbcTemplate jdbc;

    public VisitReceptionOutcomeService(MedicalVisitRepository visits, JdbcTemplate jdbc) {
        this.visits = visits;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Long slotId(Long visitId) {
        return visits.findSlotIdById(visitId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReceptionVisit notComplete(Long visitId, String reason) {
        MedicalVisit visit = lock(visitId);
        if (visit.getStatus() == VisitStatus.NOT_COMPLETED
                && Objects.equals(visit.getOutcomeReason(), reason)) {
            return ReceptionVisit.from(visit);
        }
        requireWaiting(visit);
        visit.notComplete(reason);
        visits.flush();
        return ReceptionVisit.from(visit);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReceptionVisit voidReception(Long visitId, String reason) {
        MedicalVisit visit = lock(visitId);
        if (visit.getStatus() == VisitStatus.VOIDED && Objects.equals(visit.getOutcomeReason(), reason)) {
            return ReceptionVisit.from(visit);
        }
        requireWaiting(visit);
        if (visit.getClinicalNotes() != null || visit.getDiagnosisCode() != null
                || visit.getDiagnosis() != null || visit.getTreatmentPlan() != null
                || visit.getFollowUpDate() != null
                || Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM prescription WHERE visit_id = ?)",
                        Boolean.class, visitId))) {
            throw new BusinessException("VISIT_HAS_CLINICAL_DATA",
                    "Lượt đã có dữ liệu chuyên môn hoặc đơn thuốc");
        }
        visit.voidReception(reason);
        visits.flush();
        return ReceptionVisit.from(visit);
    }

    private MedicalVisit lock(Long visitId) {
        return visits.findByIdForUpdate(visitId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lượt khám"));
    }

    private void requireWaiting(MedicalVisit visit) {
        if (visit.getStatus() != VisitStatus.WAITING) {
            throw new BusinessException("VISIT_NOT_WAITING", "Chỉ xử lý lượt đang chờ khám");
        }
    }
}
