package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.clinicdirectory.entity.ClinicRoom;
import com.htac_backend.modules.clinicdirectory.repository.ClinicRoomRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceptionRoomLockService {
    private final ClinicRoomRepository rooms;

    public ReceptionRoomLockService(ClinicRoomRepository rooms) {
        this.rooms = rooms;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActiveRoomForQueue(Long roomId) {
        ClinicRoom room = rooms.findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phòng khám"));
        if (!room.isActive()) {
            throw new BusinessException("ROOM_INACTIVE", "Phòng khám đã ngừng hoạt động");
        }
    }
}
