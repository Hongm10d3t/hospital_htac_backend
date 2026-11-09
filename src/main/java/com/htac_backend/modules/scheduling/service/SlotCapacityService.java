package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.outpatient.dto.VisitOccupancy;
import com.htac_backend.modules.outpatient.service.VisitCapacityReadService;
import com.htac_backend.modules.scheduling.dto.SlotCapacity;
import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import com.htac_backend.modules.scheduling.entity.ClinicSlotStatus;
import com.htac_backend.modules.scheduling.repository.AppointmentRepository;
import com.htac_backend.modules.scheduling.repository.ClinicSlotRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Capacity contract shared by M03 booking and M04/M05 reception flows. */
@Service
public class SlotCapacityService {
    private final ClinicSlotRepository slots;
    private final AppointmentRepository appointments;
    private final VisitCapacityReadService visits;

    public SlotCapacityService(ClinicSlotRepository slots, AppointmentRepository appointments,
            VisitCapacityReadService visits) {
        this.slots = slots;
        this.appointments = appointments;
        this.visits = visits;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public SlotCapacity occupancy(ClinicSlot slot) {
        VisitOccupancy actual = visits.occupancy(slot.getId());
        int holding = (int) appointments.findHoldingIds(slot.getId()).stream()
                .filter(id -> !actual.receivedAppointmentIds().contains(id)).count();
        int occupied = actual.actualVisits() + holding;
        return new SlotCapacity(slot.getId(), slot.getCapacity(), occupied,
                Math.max(0, slot.getCapacity() - occupied));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ClinicSlot requireOpenSlotWithSpace(Long slotId) {
        ClinicSlot slot = slots.findByIdForUpdate(slotId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khung khám"));
        if (slot.getStatus() != ClinicSlotStatus.OPEN) {
            throw new BusinessException("SLOT_CLOSED", "Khung khám đã đóng");
        }
        if (occupancy(slot).available() < 1) {
            throw new BusinessException("SLOT_FULL", "Khung khám đã hết chỗ");
        }
        return slot;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SlotCapacity requireSpaceForReception(Long slotId, Long appointmentId) {
        ClinicSlot slot = slots.findByIdForUpdate(slotId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khung khám"));
        SlotCapacity capacity = occupancy(slot);
        if (appointmentId == null) {
            if (slot.getStatus() != ClinicSlotStatus.OPEN) {
                throw new BusinessException("SLOT_CLOSED", "Khung khám đã đóng");
            }
            if (capacity.available() < 1) {
                throw new BusinessException("SLOT_FULL", "Khung khám đã hết chỗ");
            }
            return capacity;
        }
        boolean held = appointments.findHoldingIds(slotId).contains(appointmentId);
        if (!held || visits.occupancy(slotId).receivedAppointmentIds().contains(appointmentId)) {
            throw new BusinessException("APPOINTMENT_NOT_HELD", "Lịch hẹn không còn giữ suất hợp lệ");
        }
        // An existing appointment uses its reserved place even when the slot has been closed.
        return capacity;
    }
}
