package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.clinicdirectory.dto.ActiveClinicRoom;
import com.htac_backend.modules.clinicdirectory.entity.ClinicRoom;
import com.htac_backend.modules.clinicdirectory.entity.Specialty;
import com.htac_backend.modules.clinicdirectory.repository.ClinicRoomRepository;
import com.htac_backend.modules.clinicdirectory.repository.SpecialtyRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read contract for M03 when assigning a new clinic slot. */
@Service
public class ClinicDirectoryLookupService {
    private final SpecialtyRepository specialties;
    private final ClinicRoomRepository rooms;

    public ClinicDirectoryLookupService(SpecialtyRepository specialties, ClinicRoomRepository rooms) {
        this.specialties = specialties;
        this.rooms = rooms;
    }

    @Transactional(readOnly = true)
    public ActiveClinicRoom requireActiveRoom(Long roomId) {
        ClinicRoom room = rooms.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phòng khám"));
        if (!room.isActive()) {
            throw new BusinessException("ROOM_INACTIVE", "Phòng khám đã ngừng hoạt động");
        }
        requireActiveSpecialty(room.getSpecialtyId());
        return new ActiveClinicRoom(room.getId(), room.getSpecialtyId());
    }

    @Transactional(readOnly = true)
    public void requireActiveSpecialty(Long specialtyId) {
        Specialty specialty = specialties.findById(specialtyId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chuyên khoa"));
        if (!specialty.isActive()) {
            throw new BusinessException("SPECIALTY_INACTIVE", "Chuyên khoa đã ngừng hoạt động");
        }
    }
}
