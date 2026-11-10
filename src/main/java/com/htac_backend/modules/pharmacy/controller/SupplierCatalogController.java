package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.pharmacy.dto.SupplierResponse;
import com.htac_backend.modules.pharmacy.dto.SupplierWriteRequest;
import com.htac_backend.modules.pharmacy.service.SupplierCatalogService;
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
@RequestMapping("/api/v1/pharmacy/suppliers")
public class SupplierCatalogController {
    private final SupplierCatalogService suppliers;

    public SupplierCatalogController(SupplierCatalogService suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    public PageResponse<SupplierResponse> search(@RequestParam(required = false) String term,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return suppliers.search(term, active,
                PageQuery.parse(page, size, sort == null ? "id,ASC" : sort,
                        "id", Set.of("id", "code", "name")));
    }

    @GetMapping("/{id}")
    public SupplierResponse get(@PathVariable Long id) {
        return suppliers.get(id);
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierWriteRequest request) {
        SupplierResponse created = suppliers.create(request);
        return ResponseEntity.created(URI.create("/api/v1/pharmacy/suppliers/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id,
            @Valid @RequestBody SupplierWriteRequest request) {
        return suppliers.update(id, request);
    }
}
