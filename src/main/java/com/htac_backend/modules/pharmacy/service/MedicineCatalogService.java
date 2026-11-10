package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.service.MedicinePrescriptionReferenceService;
import com.htac_backend.modules.pharmacy.dto.MedicineResponse;
import com.htac_backend.modules.pharmacy.dto.MedicineWriteRequest;
import com.htac_backend.modules.pharmacy.entity.Medicine;
import com.htac_backend.modules.pharmacy.repository.MedicineRepository;
import com.htac_backend.modules.procurement.service.MedicineProcurementReferenceService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicineCatalogService {
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;
    private final MedicineRepository medicines;
    private final MedicinePrescriptionReferenceService prescriptions;
    private final MedicineProcurementReferenceService procurement;
    private final JdbcTemplate jdbc;

    public MedicineCatalogService(CurrentPharmacistActor actor, AccountRoleLookupService accounts,
            MedicineRepository medicines, MedicinePrescriptionReferenceService prescriptions,
            MedicineProcurementReferenceService procurement, JdbcTemplate jdbc) {
        this.actor = actor;
        this.accounts = accounts;
        this.medicines = medicines;
        this.prescriptions = prescriptions;
        this.procurement = procurement;
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(readOnly = true)
    public PageResponse<MedicineResponse> search(String term, Boolean active, PageQuery page) {
        requirePharmacist();
        return PageResponse.from(medicines.search(blankToNull(term), active,
                page.toPageRequest()).map(MedicineResponse::from));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(readOnly = true)
    public MedicineResponse get(Long id) {
        requirePharmacist();
        return MedicineResponse.from(medicines.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thuốc")));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MedicineResponse create(MedicineWriteRequest body) {
        requirePharmacist();
        validate(body);
        String code = body.code().trim();
        if (medicines.existsByCode(code)) {
            throw codeTaken();
        }
        Medicine created = new Medicine(code, body.name().trim(), body.activeIngredient().trim(),
                body.strength().trim(), body.dosageForm().trim(), body.baseUnit().trim(),
                body.minimumStock(), body.leadTimeDays());
        created.update(code, body.name().trim(), body.activeIngredient().trim(), body.strength().trim(),
                body.dosageForm().trim(), body.baseUnit().trim(), body.minimumStock(),
                body.leadTimeDays(), body.active());
        return MedicineResponse.from(medicines.saveAndFlush(created));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MedicineResponse update(Long id, MedicineWriteRequest body) {
        requirePharmacist();
        validate(body);
        Medicine medicine = medicines.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thuốc"));
        String code = body.code().trim();
        if (medicines.existsByCodeAndIdNot(code, id)) {
            throw codeTaken();
        }
        boolean identityChanged = !medicine.getCode().equals(code)
                || !medicine.getActiveIngredient().equals(body.activeIngredient().trim())
                || !medicine.getStrength().equals(body.strength().trim())
                || !medicine.getDosageForm().equals(body.dosageForm().trim())
                || !medicine.getBaseUnit().equals(body.baseUnit().trim());
        if (identityChanged && hasReferences(id)) {
            throw new BusinessException("MEDICINE_IDENTITY_LOCKED",
                    "Thuốc đã có nghiệp vụ; tạo mã mới khi đổi hoạt chất, hàm lượng, dạng hoặc đơn vị");
        }
        medicine.update(code, body.name().trim(), body.activeIngredient().trim(),
                body.strength().trim(), body.dosageForm().trim(), body.baseUnit().trim(),
                body.minimumStock(), body.leadTimeDays(), body.active());
        medicines.flush();
        return MedicineResponse.from(medicine);
    }

    private boolean hasReferences(Long id) {
        return prescriptions.hasPrescriptionLines(id) || procurement.hasPurchaseRequestLines(id)
                || Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM medicine_batch WHERE medicine_id = ?)",
                        Boolean.class, id));
    }

    private void validate(MedicineWriteRequest body) {
        if (body == null || invalid(body.code(), 30) || invalid(body.name(), 150)
                || invalid(body.activeIngredient(), 200) || invalid(body.strength(), 100)
                || invalid(body.dosageForm(), 50) || invalid(body.baseUnit(), 20)
                || body.minimumStock() == null || body.minimumStock().compareTo(BigDecimal.ZERO) < 0
                || body.minimumStock().scale() > 3
                || body.minimumStock().precision() - body.minimumStock().scale() > 11
                || body.leadTimeDays() == null || body.leadTimeDays() < 0
                || body.active() == null) {
            throw new InvalidRequestException("Thông tin thuốc không hợp lệ");
        }
    }

    private boolean invalid(String value, int max) {
        return value == null || value.isBlank() || value.trim().length() > max;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException codeTaken() {
        return new BusinessException("MEDICINE_CODE_TAKEN", "Mã thuốc đã được sử dụng");
    }

    private void requirePharmacist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.PHARMACIST)) {
            throw new BusinessException("PHARMACIST_INACTIVE", "Tài khoản dược sĩ không hoạt động");
        }
    }
}
