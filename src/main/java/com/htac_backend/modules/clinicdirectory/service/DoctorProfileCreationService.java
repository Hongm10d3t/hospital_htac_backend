package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.clinicdirectory.entity.DoctorProfile;
import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.clinicdirectory.entity.Specialty;
import com.htac_backend.modules.clinicdirectory.repository.DoctorProfileRepository;
import com.htac_backend.modules.clinicdirectory.repository.SpecialtyRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorProfileCreationService {
    private final DoctorProfileRepository profiles;
    private final SpecialtyRepository specialties;
    private final AccountRoleLookupService accounts;

    public DoctorProfileCreationService(DoctorProfileRepository profiles, SpecialtyRepository specialties,
            AccountRoleLookupService accounts) {
        this.profiles = profiles;
        this.specialties = specialties;
        this.accounts = accounts;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void create(Long doctorUserId, Long specialtyId) {
        if (!accounts.hasRole(doctorUserId, AccountRole.DOCTOR)) {
            throw new BusinessException("DOCTOR_ACCOUNT_REQUIRED", "Tài khoản không có vai trò bác sĩ");
        }
        Specialty specialty = specialties.findById(specialtyId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chuyên khoa"));
        if (!specialty.isActive()) {
            throw new BusinessException("SPECIALTY_INACTIVE", "Chuyên khoa đã ngừng hoạt động");
        }
        profiles.saveAndFlush(new DoctorProfile(doctorUserId, specialtyId));
    }
}
