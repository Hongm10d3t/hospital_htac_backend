package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.scheduling.dto.ClinicSlotResponse;
import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import com.htac_backend.modules.scheduling.repository.ClinicSlotRepository;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M03-owned contract for a doctor's assigned slots. */
@Service
public class DoctorSlotReadService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ClinicSlotRepository slots;

    public DoctorSlotReadService(ClinicSlotRepository slots) {
        this.slots = slots;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public PageResponse<ClinicSlotResponse> assigned(Long doctorId, LocalDate date, PageQuery page) {
        var from = date.atStartOfDay(HOSPITAL_ZONE).toInstant();
        var to = date.plusDays(1).atStartOfDay(HOSPITAL_ZONE).toInstant();
        return PageResponse.from(slots.search(null, doctorId, from, to, page.toPageRequest())
                .map(ClinicSlotResponse::from));
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public ClinicSlotResponse requireAssigned(Long slotId, Long doctorId) {
        ClinicSlot slot = slots.findById(slotId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khung khám được phân công"));
        if (!slot.getDoctorId().equals(doctorId)) {
            throw new NotFoundException("Không tìm thấy khung khám được phân công");
        }
        return ClinicSlotResponse.from(slot);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ClinicSlotResponse requireAssignedForUpdate(Long slotId, Long doctorId) {
        ClinicSlot slot = slots.findByIdForUpdate(slotId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khung khám được phân công"));
        if (!slot.getDoctorId().equals(doctorId)) {
            throw new NotFoundException("Không tìm thấy khung khám được phân công");
        }
        return ClinicSlotResponse.from(slot);
    }
}
