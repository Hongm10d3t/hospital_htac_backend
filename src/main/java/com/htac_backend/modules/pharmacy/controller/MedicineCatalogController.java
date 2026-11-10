package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.pharmacy.dto.MedicineResponse;
import com.htac_backend.modules.pharmacy.dto.MedicineWriteRequest;
import com.htac_backend.modules.pharmacy.service.MedicineCatalogService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacy/medicines")
public class MedicineCatalogController {
    private final MedicineCatalogService medicines;

    public MedicineCatalogController(MedicineCatalogService medicines) {
        this.medicines = medicines;
    }

    @GetMapping
    public PageResponse<MedicineResponse> search(@RequestParam(required = false) String term,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return medicines.search(term, active,
                PageQuery.parse(page, size, sort == null ? "id,ASC" : sort,
                        "id", Set.of("id", "code", "name")));
    }

    @GetMapping("/{id}")
    public MedicineResponse get(@PathVariable Long id) {
        return medicines.get(id);
    }

    @PostMapping
    public ResponseEntity<MedicineResponse> create(@Valid @RequestBody MedicineWriteRequest request) {
        MedicineResponse created = medicines.create(request);
        return ResponseEntity.created(URI.create("/api/v1/pharmacy/medicines/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public MedicineResponse update(@PathVariable Long id,
            @Valid @RequestBody MedicineWriteRequest request) {
        return medicines.update(id, request);
    }
}
