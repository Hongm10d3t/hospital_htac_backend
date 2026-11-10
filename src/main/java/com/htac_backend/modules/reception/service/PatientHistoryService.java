package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.dto.PatientPrescriptionDetail;
import com.htac_backend.modules.outpatient.dto.PatientPrescriptionSummary;
import com.htac_backend.modules.outpatient.dto.PatientVisitDetail;
import com.htac_backend.modules.outpatient.dto.PatientVisitSummary;
import com.htac_backend.modules.outpatient.service.PatientHistoryReadService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientHistoryService {
    private final CurrentPatientActor actor;
    private final AccountRoleLookupService accounts;
    private final PatientAccountLookupService patients;
    private final PatientHistoryReadService history;

    public PatientHistoryService(CurrentPatientActor actor, AccountRoleLookupService accounts,
            PatientAccountLookupService patients, PatientHistoryReadService history) {
        this.actor = actor;
        this.accounts = accounts;
        this.patients = patients;
        this.history = history;
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional(readOnly = true)
    public PageResponse<PatientVisitSummary> visits(LocalDate from, LocalDate to, PageQuery page) {
        checkDates(from, to);
        return history.visits(ownPatientId(), from, to, page);
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional(readOnly = true)
    public PatientVisitDetail visit(Long visitId) {
        return history.visit(ownPatientId(), visitId);
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional(readOnly = true)
    public PageResponse<PatientPrescriptionSummary> prescriptions(LocalDate from, LocalDate to,
            PageQuery page) {
        checkDates(from, to);
        return history.prescriptions(ownPatientId(), from, to, page);
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional(readOnly = true)
    public PatientPrescriptionDetail prescription(Long prescriptionId) {
        return history.prescription(ownPatientId(), prescriptionId);
    }

    private Long ownPatientId() {
        Long accountId = actor.userId();
        if (!accounts.hasActiveRole(accountId, AccountRole.PATIENT)) {
            throw new BusinessException("PATIENT_ACCOUNT_INACTIVE", "Tài khoản bệnh nhân không hoạt động");
        }
        return patients.requirePatientId(accountId);
    }

    private void checkDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("Khoảng ngày không hợp lệ");
        }
    }
}
