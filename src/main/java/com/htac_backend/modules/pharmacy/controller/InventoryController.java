package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.pharmacy.dto.MedicineInventoryResponse;
import com.htac_backend.modules.pharmacy.dto.StockMovementResponse;
import com.htac_backend.modules.pharmacy.service.InventoryQueryService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacy/inventory")
public class InventoryController {
    private final InventoryQueryService inventory;

    public InventoryController(InventoryQueryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    public PageResponse<MedicineInventoryResponse> search(@RequestParam(required = false) String term,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return inventory.search(term, active, PageQuery.parse(page, size,
                sort == null ? "code,ASC" : sort, "code", Set.of("id", "code", "name")));
    }

    @GetMapping("/medicines/{id}")
    public MedicineInventoryResponse medicine(@PathVariable Long id) {
        return inventory.medicine(id);
    }

    @GetMapping("/batches/{id}/movements")
    public PageResponse<StockMovementResponse> movements(@PathVariable Long id,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return inventory.movements(id, location, bucket,
                PageQuery.parse(page, size, sort == null ? "occurredAt,DESC" : sort,
                        "occurredAt", Set.of("occurredAt", "documentId", "itemId")));
    }
}
