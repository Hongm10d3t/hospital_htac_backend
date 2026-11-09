package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.clinicdirectory.dto.CreateDoctorProfileRequest;
import com.htac_backend.modules.clinicdirectory.dto.DoctorProfileAdminResponse;
import com.htac_backend.modules.clinicdirectory.dto.UpdateDoctorProfileRequest;
import com.htac_backend.modules.clinicdirectory.entity.DoctorProfile;
import com.htac_backend.modules.clinicdirectory.entity.Specialty;
import com.htac_backend.modules.clinicdirectory.repository.DoctorProfileRepository;
import com.htac_backend.modules.clinicdirectory.repository.SpecialtyRepository;
import com.htac_backend.modules.scheduling.service.ClinicSlotReferenceService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.net.URI;
import java.time.Year;
import java.time.ZoneId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class DoctorProfileAdminService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final DoctorProfileRepository profiles;
    private final SpecialtyRepository specialties;
    private final DoctorProfileCreationService creation;
    private final ClinicSlotReferenceService slots;

    public DoctorProfileAdminService(DoctorProfileRepository profiles, SpecialtyRepository specialties,
            DoctorProfileCreationService creation, ClinicSlotReferenceService slots) {
        this.profiles = profiles;
        this.specialties = specialties;
        this.creation = creation;
        this.slots = slots;
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorProfileAdminResponse> list(Long specialtyId, PageQuery page) {
        if (specialtyId != null && specialtyId < 1) {
            throw new InvalidRequestException("specialtyId không hợp lệ");
        }
        return PageResponse.from(profiles.search(specialtyId, page.toPageRequest())
                .map(DoctorProfileAdminResponse::from));
    }

    @Transactional(readOnly = true)
    public DoctorProfileAdminResponse get(Long userId) {
        return DoctorProfileAdminResponse.from(profiles.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ")));
    }

    @Transactional
    public DoctorProfileAdminResponse create(CreateDoctorProfileRequest body) {
        if (profiles.existsById(body.userId())) {
            throw new BusinessException("DOCTOR_PROFILE_EXISTS", "Bác sĩ đã có hồ sơ chuyên môn");
        }
        validateFields(body.avatarUrl(), body.practiceStartYear());
        creation.create(body.userId(), body.specialtyId());
        DoctorProfile profile = profiles.findById(body.userId()).orElseThrow();
        profile.update(body.specialtyId(), blankToNull(body.avatarUrl()), blankToNull(body.professionalTitle()),
                blankToNull(body.position()), body.practiceStartYear(), blankToNull(body.introduction()),
                blankToNull(body.clinicalExpertise()), blankToNull(body.certifications()));
        profiles.flush();
        return DoctorProfileAdminResponse.from(profile);
    }

    @Transactional
    public DoctorProfileAdminResponse update(Long userId, UpdateDoctorProfileRequest body) {
        DoctorProfile profile = profiles.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
        validateFields(body.avatarUrl(), body.practiceStartYear());
        if (!profile.getSpecialtyId().equals(body.specialtyId())) {
            requireActiveSpecialty(body.specialtyId());
            if (slots.doctorHasSlots(userId)) {
                throw new BusinessException("DOCTOR_SPECIALTY_LOCKED",
                        "Bác sĩ đã có lịch, không thể đổi chuyên khoa");
            }
        }
        profile.update(body.specialtyId(), blankToNull(body.avatarUrl()), blankToNull(body.professionalTitle()),
                blankToNull(body.position()), body.practiceStartYear(), blankToNull(body.introduction()),
                blankToNull(body.clinicalExpertise()), blankToNull(body.certifications()));
        profiles.flush();
        return DoctorProfileAdminResponse.from(profile);
    }

    private void requireActiveSpecialty(Long id) {
        Specialty specialty = specialties.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chuyên khoa"));
        if (!specialty.isActive()) {
            throw new BusinessException("SPECIALTY_INACTIVE", "Chuyên khoa đã ngừng hoạt động");
        }
    }

    private void validateFields(String avatarUrl, Short practiceStartYear) {
        if (practiceStartYear != null && (practiceStartYear < 1900
                || practiceStartYear > Year.now(HOSPITAL_ZONE).getValue())) {
            throw new InvalidRequestException("Năm bắt đầu hành nghề không hợp lệ");
        }
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(avatarUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("avatarUrl phải là URL HTTPS hợp lệ");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
