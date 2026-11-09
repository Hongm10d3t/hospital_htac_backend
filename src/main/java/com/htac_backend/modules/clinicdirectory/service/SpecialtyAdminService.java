package com.htac_backend.modules.clinicdirectory.service;

import com.htac_backend.modules.clinicdirectory.dto.CreateSpecialtyRequest;
import com.htac_backend.modules.clinicdirectory.dto.SpecialtyResponse;
import com.htac_backend.modules.clinicdirectory.dto.UpdateSpecialtyRequest;
import com.htac_backend.modules.clinicdirectory.entity.Specialty;
import com.htac_backend.modules.clinicdirectory.repository.SpecialtyRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class SpecialtyAdminService {
    private final SpecialtyRepository specialties;

    public SpecialtyAdminService(SpecialtyRepository specialties) {
        this.specialties = specialties;
    }

    @Transactional(readOnly = true)
    public PageResponse<SpecialtyResponse> list(Boolean active, PageQuery page) {
        var result = active == null ? specialties.findAll(page.toPageRequest())
                : specialties.findByActive(active, page.toPageRequest());
        return PageResponse.from(result.map(SpecialtyResponse::from));
    }

    @Transactional(readOnly = true)
    public SpecialtyResponse get(Long id) {
        return SpecialtyResponse.from(specialties.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chuyên khoa")));
    }

    @Transactional
    public SpecialtyResponse create(CreateSpecialtyRequest body) {
        if (specialties.existsByCode(body.code())) {
            throw new BusinessException("SPECIALTY_CODE_TAKEN", "Mã chuyên khoa đã được sử dụng");
        }
        return SpecialtyResponse.from(specialties.saveAndFlush(new Specialty(body.code(), body.name().trim())));
    }

    @Transactional
    public SpecialtyResponse update(Long id, UpdateSpecialtyRequest body) {
        Specialty specialty = locked(id);
        if (!specialty.getCode().equals(body.code()) && specialties.existsByCodeAndIdNot(body.code(), id)) {
            throw new BusinessException("SPECIALTY_CODE_TAKEN", "Mã chuyên khoa đã được sử dụng");
        }
        specialty.update(body.code(), body.name().trim());
        specialties.flush();
        return SpecialtyResponse.from(specialty);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        Specialty specialty = locked(id);
        specialty.setActive(active);
        specialties.flush();
    }

    private Specialty locked(Long id) {
        return specialties.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chuyên khoa"));
    }
}
