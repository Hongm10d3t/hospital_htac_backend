package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.pharmacy.dto.MedicineBatchResponse;
import com.htac_backend.modules.pharmacy.dto.MedicineBatchWriteRequest;
import com.htac_backend.modules.pharmacy.service.MedicineBatchService;
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
@RequestMapping("/api/v1/pharmacy/batches")
public class MedicineBatchController {
    private final MedicineBatchService batches;

    public MedicineBatchController(MedicineBatchService batches) {
        this.batches = batches;
    }

    @GetMapping
    public PageResponse<MedicineBatchResponse> search(@RequestParam(required = false) Long medicineId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String term,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return batches.search(medicineId, status, term, PageQuery.parse(page, size,
                sort == null ? "id,ASC" : sort, "id", Set.of("id", "batchNumber", "expiryDate")));
    }

    @GetMapping("/{id}")
    public MedicineBatchResponse get(@PathVariable Long id) {
        return batches.get(id);
    }

    @PostMapping
    public ResponseEntity<MedicineBatchResponse> create(@Valid @RequestBody MedicineBatchWriteRequest body) {
        MedicineBatchResponse created = batches.create(body);
        return ResponseEntity.created(URI.create("/api/v1/pharmacy/batches/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public MedicineBatchResponse update(@PathVariable Long id, @Valid @RequestBody MedicineBatchWriteRequest body) {
        return batches.update(id, body);
    }
}
