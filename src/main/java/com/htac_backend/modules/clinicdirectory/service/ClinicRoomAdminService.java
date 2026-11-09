package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.clinicdirectory.dto.ClinicRoomResponse;
import com.htac_backend.modules.clinicdirectory.dto.CreateClinicRoomRequest;
import com.htac_backend.modules.clinicdirectory.dto.UpdateClinicRoomRequest;
import com.htac_backend.modules.clinicdirectory.entity.ClinicRoom;
import com.htac_backend.modules.clinicdirectory.entity.Specialty;
import com.htac_backend.modules.clinicdirectory.repository.ClinicRoomRepository;
import com.htac_backend.modules.clinicdirectory.repository.SpecialtyRepository;
import com.htac_backend.modules.scheduling.service.ClinicSlotReferenceService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class ClinicRoomAdminService {
    private final ClinicRoomRepository rooms;
    private final SpecialtyRepository specialties;
    private final ClinicSlotReferenceService slotReferences;

    public ClinicRoomAdminService(ClinicRoomRepository rooms, SpecialtyRepository specialties,
            ClinicSlotReferenceService slotReferences) {
        this.rooms = rooms;
        this.specialties = specialties;
        this.slotReferences = slotReferences;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClinicRoomResponse> list(Long specialtyId, Boolean active, PageQuery page) {
        if (specialtyId != null && specialtyId < 1) {
            throw new InvalidRequestException("specialtyId không hợp lệ");
        }
        return PageResponse.from(rooms.search(specialtyId, active, page.toPageRequest())
                .map(ClinicRoomResponse::from));
    }

    @Transactional(readOnly = true)
    public ClinicRoomResponse get(Long id) {
        return ClinicRoomResponse.from(rooms.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phòng khám")));
    }

    @Transactional
    public ClinicRoomResponse create(CreateClinicRoomRequest body) {
        requireActiveSpecialty(body.specialtyId());
        if (rooms.existsByCode(body.code())) {
            throw new BusinessException("ROOM_CODE_TAKEN", "Mã phòng khám đã được sử dụng");
        }
        return ClinicRoomResponse.from(rooms.saveAndFlush(new ClinicRoom(body.specialtyId(), body.code(),
                body.name().trim(), blankToNull(body.location()))));
    }

    @Transactional
    public ClinicRoomResponse update(Long id, UpdateClinicRoomRequest body) {
        ClinicRoom room = locked(id);
        if (!room.getCode().equals(body.code()) && rooms.existsByCodeAndIdNot(body.code(), id)) {
            throw new BusinessException("ROOM_CODE_TAKEN", "Mã phòng khám đã được sử dụng");
        }
        if (!room.getSpecialtyId().equals(body.specialtyId())) {
            requireActiveSpecialty(body.specialtyId());
            if (slotReferences.roomHasSlots(id)) {
                throw new BusinessException("ROOM_SPECIALTY_LOCKED",
                        "Phòng đã có lịch sử, không thể đổi chuyên khoa");
            }
        }
        room.update(body.specialtyId(), body.code(), body.name().trim(), blankToNull(body.location()));
        rooms.flush();
        return ClinicRoomResponse.from(room);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        ClinicRoom room = locked(id);
        if (active) {
            requireActiveSpecialty(room.getSpecialtyId());
        }
        room.setActive(active);
        rooms.flush();
    }

    private ClinicRoom locked(Long id) {
        return rooms.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phòng khám"));
    }

    private Specialty requireActiveSpecialty(Long id) {
        Specialty specialty = specialties.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chuyên khoa"));
        if (!specialty.isActive()) {
            throw new BusinessException("SPECIALTY_INACTIVE", "Chuyên khoa đã ngừng hoạt động");
        }
        return specialty;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
