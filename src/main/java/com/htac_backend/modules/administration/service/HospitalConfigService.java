package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.dto.HospitalConfigResponse;
import com.htac_backend.modules.administration.dto.HospitalConfigWriteResult;
import com.htac_backend.modules.administration.dto.UpsertHospitalConfigRequest;
import com.htac_backend.modules.administration.entity.HospitalConfig;
import com.htac_backend.modules.administration.repository.HospitalConfigRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HospitalConfigService {
    private final HospitalConfigRepository configs;

    public HospitalConfigService(HospitalConfigRepository configs) {
        this.configs = configs;
    }

    @Transactional(readOnly = true)
    public HospitalConfigResponse publicInfo() {
        return HospitalConfigResponse.from(configs.findById(HospitalConfig.SINGLETON_ID)
                .orElseThrow(() -> new NotFoundException("Chưa có thông tin bệnh viện")));
    }

    @Transactional(readOnly = true)
    public int maxAdvanceBookingDays() {
        return configs.findById(HospitalConfig.SINGLETON_ID)
                .map(HospitalConfig::getAdvanceBookingDays)
                .orElseThrow(() -> new BusinessException("HOSPITAL_CONFIG_MISSING",
                        "Chưa cấu hình thời hạn đặt trước"));
    }

    /** M03 supplies the business date in the hospital timezone. Both bounds are inclusive. */
    @Transactional(readOnly = true)
    public boolean isWithinAdvanceWindow(LocalDate requestedDate, LocalDate hospitalDate) {
        if (requestedDate == null || hospitalDate == null) {
            throw new InvalidRequestException("Ngày đặt khám không hợp lệ");
        }
        return !requestedDate.isBefore(hospitalDate)
                && !requestedDate.isAfter(hospitalDate.plusDays(maxAdvanceBookingDays()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public HospitalConfigWriteResult upsert(UpsertHospitalConfigRequest body) {
        String name = body.name().trim();
        String address = body.address().trim();
        String phone = body.phone().trim();
        if (name.isBlank() || address.isBlank() || phone.isBlank() || body.advanceBookingDays() < 1) {
            throw new InvalidRequestException("Thông tin bệnh viện không hợp lệ");
        }
        String email = blankToNull(body.email());
        String logoUrl = blankToNull(body.logoUrl());
        String description = blankToNull(body.description());
        requireSafeLogoUrl(logoUrl);

        HospitalConfig config = configs.findSingletonForUpdate().orElse(null);
        boolean created = config == null;
        if (created) {
            config = new HospitalConfig(name, address, phone, email, logoUrl, description,
                    body.advanceBookingDays());
            configs.saveAndFlush(config);
        } else {
            config.replace(name, address, phone, email, logoUrl, description,
                    body.advanceBookingDays());
            configs.flush();
        }
        return new HospitalConfigWriteResult(HospitalConfigResponse.from(config), created);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireSafeLogoUrl(String logoUrl) {
        if (logoUrl == null) {
            return;
        }
        try {
            URI uri = URI.create(logoUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("logoUrl phải là URL HTTPS hợp lệ");
        }
    }
}
