package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.administration.service.AccountPublicIdentityService;
import com.htac_backend.modules.clinicdirectory.dto.DoctorPublicResponse;
import com.htac_backend.modules.clinicdirectory.entity.DoctorProfile;
import com.htac_backend.modules.clinicdirectory.entity.Specialty;
import com.htac_backend.modules.clinicdirectory.repository.DoctorProfileRepository;
import com.htac_backend.modules.clinicdirectory.repository.SpecialtyRepository;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorPublicService {
    private final DoctorProfileRepository profiles;
    private final SpecialtyRepository specialties;
    private final AccountPublicIdentityService identities;

    public DoctorPublicService(DoctorProfileRepository profiles, SpecialtyRepository specialties,
            AccountPublicIdentityService identities) {
        this.profiles = profiles;
        this.specialties = specialties;
        this.identities = identities;
    }

    @Transactional(readOnly = true)
    public PageResponse<DoctorPublicResponse> list(Long specialtyId, PageQuery page) {
        if (specialtyId != null && specialtyId < 1) {
            throw new InvalidRequestException("specialtyId không hợp lệ");
        }
        Page<DoctorProfile> results = profiles.searchPublic(specialtyId, page.toPageRequest());
        List<DoctorProfile> content = results.getContent();
        Map<Long, String> names = identities.fullNamesFor(content.stream().map(DoctorProfile::getUserId).toList());
        Map<Long, Specialty> specialtyMap = specialties.findAllById(
                content.stream().map(DoctorProfile::getSpecialtyId).distinct().toList()).stream()
                .collect(Collectors.toMap(Specialty::getId, Function.identity()));
        return PageResponse.from(results.map(profile -> DoctorPublicResponse.from(profile,
                names.get(profile.getUserId()), specialtyMap.get(profile.getSpecialtyId()).getName())));
    }

    @Transactional(readOnly = true)
    public DoctorPublicResponse get(Long userId) {
        DoctorProfile profile = profiles.findPublicByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ công khai"));
        String fullName = identities.fullNamesFor(List.of(userId)).get(userId);
        String specialtyName = specialties.findById(profile.getSpecialtyId()).orElseThrow().getName();
        return DoctorPublicResponse.from(profile, fullName, specialtyName);
    }
}
