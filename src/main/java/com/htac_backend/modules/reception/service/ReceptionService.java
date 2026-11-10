package com.htac_backend.modules.reception.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.clinicdirectory.service.ReceptionRoomLockService;
import com.htac_backend.modules.outpatient.dto.ReceptionVisit;
import com.htac_backend.modules.outpatient.service.VisitReceptionService;
import com.htac_backend.modules.reception.dto.DirectReceptionRequest;
import com.htac_backend.modules.scheduling.dto.ReceptionBooking;
import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import com.htac_backend.modules.scheduling.service.AppointmentReceptionLookupService;
import com.htac_backend.modules.scheduling.service.SlotCapacityService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceptionService {
    private final CurrentPatientActor actor;
    private final AccountRoleLookupService accounts;
    private final ReceptionPatientLookupService patients;
    private final AppointmentReceptionLookupService bookings;
    private final SlotCapacityService capacity;
    private final ReceptionRoomLockService rooms;
    private final VisitReceptionService visits;

    public ReceptionService(CurrentPatientActor actor, AccountRoleLookupService accounts,
            ReceptionPatientLookupService patients, AppointmentReceptionLookupService bookings,
            SlotCapacityService capacity, ReceptionRoomLockService rooms, VisitReceptionService visits) {
        this.actor = actor;
        this.accounts = accounts;
        this.patients = patients;
        this.bookings = bookings;
        this.capacity = capacity;
        this.rooms = rooms;
        this.visits = visits;
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceptionVisit receiveBooked(Long appointmentId) {
        requireActiveReceptionist();
        Long slotId = bookings.slotId(appointmentId);
        // Lock order: clinic_slot, appointment, clinic_room, medical_visit.
        ClinicSlot slot = capacity.lockForReception(slotId);
        ReceptionVisit previous = visits.byAppointment(appointmentId).orElse(null);
        if (previous != null) {
            return previous;
        }
        ReceptionBooking booking = bookings.requireHeld(appointmentId);
        requireSameDay(slot, Instant.now());
        capacity.requireSpaceForReception(slotId, appointmentId);
        patients.requirePatient(booking.patientId());
        rooms.requireActiveRoomForQueue(slot.getRoomId());
        return visits.create(booking.patientId(), appointmentId, slotId, slot.getRoomId(),
                Instant.now(), booking.reason());
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceptionVisit receiveDirect(DirectReceptionRequest request) {
        requireActiveReceptionist();
        ClinicSlot slot = capacity.lockForReception(request.slotId());
        requireSameDay(slot, Instant.now());
        capacity.requireSpaceForReception(request.slotId(), null);
        patients.requirePatient(request.patientId());
        rooms.requireActiveRoomForQueue(slot.getRoomId());
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        return visits.create(request.patientId(), null, slot.getId(), slot.getRoomId(), Instant.now(), reason);
    }

    @PreAuthorize("hasRole('RECEPTIONIST')")
    @Transactional(readOnly = true)
    public PageResponse<ReceptionVisit> waiting(LocalDate date, Long roomId, Long slotId, PageQuery page) {
        requireActiveReceptionist();
        return visits.waiting(date == null ? LocalDate.now(VisitReceptionService.HOSPITAL_ZONE) : date,
                roomId, slotId, page);
    }

    private void requireSameDay(ClinicSlot slot, Instant now) {
        if (!slot.getStartAt().atZone(VisitReceptionService.HOSPITAL_ZONE).toLocalDate()
                .equals(now.atZone(VisitReceptionService.HOSPITAL_ZONE).toLocalDate())) {
            throw new BusinessException("SLOT_NOT_TODAY", "Khung khám không thuộc ngày tiếp nhận hiện tại");
        }
    }

    private void requireActiveReceptionist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.RECEPTIONIST)) {
            throw new BusinessException("RECEPTIONIST_INACTIVE", "Tài khoản tiếp đón không hoạt động");
        }
    }
}
