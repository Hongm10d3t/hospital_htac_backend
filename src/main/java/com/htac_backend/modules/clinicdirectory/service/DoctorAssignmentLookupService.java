package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.clinicdirectory.dto.ActiveDoctor;
import com.htac_backend.modules.clinicdirectory.entity.DoctorProfile;
import com.htac_backend.modules.clinicdirectory.repository.DoctorProfileRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read contract for M03 when assigning a doctor to a new slot. */
@Service
public class DoctorAssignmentLookupService {
    private final DoctorProfileRepository profiles;
    private final ClinicDirectoryLookupService directory;
    private final AccountRoleLookupService accounts;

    public DoctorAssignmentLookupService(DoctorProfileRepository profiles,
            ClinicDirectoryLookupService directory, AccountRoleLookupService accounts) {
        this.profiles = profiles;
        this.directory = directory;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public ActiveDoctor requireActiveDoctor(Long userId) {
        DoctorProfile profile = profiles.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
        if (!accounts.hasActiveRole(userId, AccountRole.DOCTOR)) {
            throw new BusinessException("DOCTOR_INACTIVE", "Bác sĩ không còn hoạt động");
        }
        directory.requireActiveSpecialty(profile.getSpecialtyId());
        return new ActiveDoctor(userId, profile.getSpecialtyId());
    }
}
