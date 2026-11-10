package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.dto.ReceptionVisit;
import com.htac_backend.modules.outpatient.service.VisitReceptionOutcomeService;
import com.htac_backend.modules.scheduling.service.ReceptionAppointmentService;
import com.htac_backend.modules.scheduling.service.SlotCapacityService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceptionOutcomeService {
    private final CurrentPatientActor actor;
    private final AccountRoleLookupService accounts;
    private final SlotCapacityService capacity;
    private final VisitReceptionOutcomeService visits;
    private final ReceptionAppointmentService appointments;

    public ReceptionOutcomeService(CurrentPatientActor actor, AccountRoleLookupService accounts,
            SlotCapacityService capacity, VisitReceptionOutcomeService visits,
            ReceptionAppointmentService appointments) {
        this.actor = actor;
        this.accounts = accounts;
        this.capacity = capacity;
        this.visits = visits;
        this.appointments = appointments;
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceptionVisit notComplete(Long visitId, String reason) {
        requireActiveReceptionist();
        String cleaned = clean(reason);
        capacity.lockForReception(visits.slotId(visitId));
        ReceptionVisit updated = visits.notComplete(visitId, cleaned);
        if (updated.appointmentId() != null) {
            appointments.notCompleteFromVisit(updated.appointmentId());
        }
        return updated;
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceptionVisit voidReception(Long visitId, String reason) {
        requireActiveReceptionist();
        String cleaned = clean(reason);
        capacity.lockForReception(visits.slotId(visitId));
        ReceptionVisit updated = visits.voidReception(visitId, cleaned);
        if (updated.appointmentId() != null) {
            appointments.notComplete(updated.appointmentId(), cleaned);
        }
        return updated;
    }

    private String clean(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 3000) {
            throw new InvalidRequestException("Lý do xử lý lượt không hợp lệ");
        }
        return reason.trim();
    }

    private void requireActiveReceptionist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.RECEPTIONIST)) {
            throw new BusinessException("RECEPTIONIST_INACTIVE", "Tài khoản tiếp đón không hoạt động");
        }
    }
}
