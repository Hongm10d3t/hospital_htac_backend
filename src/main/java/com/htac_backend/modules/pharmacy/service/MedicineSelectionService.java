package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.pharmacy.dto.MedicineSelectionResponse;
import com.htac_backend.modules.pharmacy.repository.MedicineRepository;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicineSelectionService {
    private final MedicineRepository medicines;

    public MedicineSelectionService(MedicineRepository medicines) {
        this.medicines = medicines;
    }

    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional(readOnly = true)
    public PageResponse<MedicineSelectionResponse> search(String term, PageQuery page) {
        return PageResponse.from(medicines.search(term == null || term.isBlank() ? null : term.trim(),
                true, page.toPageRequest()).map(MedicineSelectionResponse::from));
    }
}
