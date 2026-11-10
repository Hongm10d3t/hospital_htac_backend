package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.pharmacy.dto.SupplierResponse;
import com.htac_backend.modules.pharmacy.dto.SupplierWriteRequest;
import com.htac_backend.modules.pharmacy.entity.Supplier;
import com.htac_backend.modules.pharmacy.repository.SupplierRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierCatalogService {
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;
    private final SupplierRepository suppliers;

    public SupplierCatalogService(CurrentPharmacistActor actor, AccountRoleLookupService accounts,
            SupplierRepository suppliers) {
        this.actor = actor;
        this.accounts = accounts;
        this.suppliers = suppliers;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> search(String term, Boolean active, PageQuery page) {
        requirePharmacist();
        return PageResponse.from(suppliers.search(blankToNull(term), active,
                page.toPageRequest()).map(SupplierResponse::from));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        requirePharmacist();
        return SupplierResponse.from(suppliers.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhà cung cấp")));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SupplierResponse create(SupplierWriteRequest body) {
        requirePharmacist();
        validate(body);
        String code = body.code().trim();
        if (suppliers.existsByCode(code)) {
            throw codeTaken();
        }
        Supplier created = new Supplier(code, body.name().trim(), blankToNull(body.phone()),
                blankToNull(body.address()));
        created.update(code, body.name().trim(), blankToNull(body.phone()),
                blankToNull(body.address()), body.active());
        return SupplierResponse.from(suppliers.saveAndFlush(created));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SupplierResponse update(Long id, SupplierWriteRequest body) {
        requirePharmacist();
        validate(body);
        Supplier supplier = suppliers.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhà cung cấp"));
        String code = body.code().trim();
        if (suppliers.existsByCodeAndIdNot(code, id)) {
            throw codeTaken();
        }
        supplier.update(code, body.name().trim(), blankToNull(body.phone()),
                blankToNull(body.address()), body.active());
        suppliers.flush();
        return SupplierResponse.from(supplier);
    }

    private void validate(SupplierWriteRequest body) {
        if (body == null || invalid(body.code(), 30) || invalid(body.name(), 200)
                || (body.phone() != null && body.phone().length() > 20)
                || (body.address() != null && body.address().length() > 2000)
                || body.active() == null) {
            throw new InvalidRequestException("Thông tin nhà cung cấp không hợp lệ");
        }
    }

    private boolean invalid(String value, int max) {
        return value == null || value.isBlank() || value.trim().length() > max;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException codeTaken() {
        return new BusinessException("SUPPLIER_CODE_TAKEN", "Mã nhà cung cấp đã được sử dụng");
    }

    private void requirePharmacist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.PHARMACIST)) {
            throw new BusinessException("PHARMACIST_INACTIVE", "Tài khoản dược sĩ không hoạt động");
        }
    }
}
