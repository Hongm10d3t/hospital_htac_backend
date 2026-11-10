package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.pharmacy.dto.MedicineSelectionResponse;
import com.htac_backend.modules.pharmacy.service.MedicineSelectionService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctor/medicines")
public class DoctorMedicineSelectionController {
    private final MedicineSelectionService medicines;

    public DoctorMedicineSelectionController(MedicineSelectionService medicines) {
        this.medicines = medicines;
    }

    @GetMapping
    public PageResponse<MedicineSelectionResponse> search(@RequestParam(required = false) String term,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return medicines.search(term, PageQuery.parse(page, size, sort == null ? "name,ASC" : sort,
                "name", Set.of("id", "code", "name")));
    }
}
