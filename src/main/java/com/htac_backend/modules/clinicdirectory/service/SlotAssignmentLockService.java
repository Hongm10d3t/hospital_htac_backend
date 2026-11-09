package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.clinicdirectory.dto.SlotAssignment;
import com.htac_backend.modules.clinicdirectory.entity.ClinicRoom;
import com.htac_backend.modules.clinicdirectory.entity.DoctorProfile;
import com.htac_backend.modules.clinicdirectory.repository.ClinicRoomRepository;
import com.htac_backend.modules.clinicdirectory.repository.DoctorProfileRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Locks assignment owners in account, doctor, room order for slot writes. */
@Service
public class SlotAssignmentLockService {
    private final AccountRoleLookupService accounts;
    private final DoctorProfileRepository doctors;
    private final ClinicRoomRepository rooms;
    private final ClinicDirectoryLookupService directory;

    public SlotAssignmentLockService(AccountRoleLookupService accounts, DoctorProfileRepository doctors,
            ClinicRoomRepository rooms, ClinicDirectoryLookupService directory) {
        this.accounts = accounts;
        this.doctors = doctors;
        this.rooms = rooms;
        this.directory = directory;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SlotAssignment requireForNewSlot(Long doctorId, Long roomId) {
        accounts.requireActiveRoleForUpdate(doctorId, AccountRole.DOCTOR);
        DoctorProfile doctor = doctors.findByIdForUpdate(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
        ClinicRoom room = rooms.findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phòng khám"));
        if (!room.isActive()) {
            throw new BusinessException("ROOM_INACTIVE", "Phòng khám đã ngừng hoạt động");
        }
        directory.requireActiveSpecialty(room.getSpecialtyId());
        if (!doctor.getSpecialtyId().equals(room.getSpecialtyId())) {
            throw new BusinessException("SPECIALTY_MISMATCH", "Bác sĩ và phòng khám khác chuyên khoa");
        }
        return new SlotAssignment(doctorId, roomId, room.getSpecialtyId());
    }
}
