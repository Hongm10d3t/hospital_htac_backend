package com.htac_backend.modules.procurement.controller;

import com.htac_backend.modules.procurement.dto.PurchaseDecisionRequest;
import com.htac_backend.modules.procurement.dto.PurchaseOrderRequest;
import com.htac_backend.modules.procurement.dto.PurchaseRequestResponse;
import com.htac_backend.modules.procurement.dto.PurchaseRequestWrite;
import com.htac_backend.modules.procurement.service.PurchaseRequestService;
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
@RequestMapping("/api/v1/procurement/purchase-requests")
public class PurchaseRequestController {
    private final PurchaseRequestService requests;

    public PurchaseRequestController(PurchaseRequestService requests) { this.requests = requests; }

    @GetMapping
    public PageResponse<PurchaseRequestResponse> search(@RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return requests.search(status, supplierId, PageQuery.parse(page, size,
                sort == null ? "id,DESC" : sort, "id", Set.of("id", "requestCode", "status",
                        "expectedDeliveryDate")));
    }

    @GetMapping("/{id}")
    public PurchaseRequestResponse get(@PathVariable Long id) { return requests.get(id); }

    @PostMapping
    public ResponseEntity<PurchaseRequestResponse> create(@Valid @RequestBody PurchaseRequestWrite body) {
        var created = requests.create(body);
        return ResponseEntity.created(URI.create("/api/v1/procurement/purchase-requests/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public PurchaseRequestResponse update(@PathVariable Long id,
            @Valid @RequestBody PurchaseRequestWrite body) { return requests.update(id, body); }

    @PostMapping("/{id}/submit")
    public PurchaseRequestResponse submit(@PathVariable Long id) { return requests.submit(id); }

    @PostMapping("/{id}/cancel")
    public PurchaseRequestResponse cancel(@PathVariable Long id) { return requests.cancel(id); }

    @PostMapping("/{id}/approve")
    public PurchaseRequestResponse approve(@PathVariable Long id,
            @RequestBody(required = false) PurchaseDecisionRequest body) {
        return requests.approve(id, body == null ? null : body.reason());
    }

    @PostMapping("/{id}/reject")
    public PurchaseRequestResponse reject(@PathVariable Long id,
            @RequestBody PurchaseDecisionRequest body) {
        return requests.reject(id, body == null ? null : body.reason());
    }

    @PostMapping("/{id}/order")
    public PurchaseRequestResponse order(@PathVariable Long id,
            @Valid @RequestBody PurchaseOrderRequest body) { return requests.order(id, body); }

    @PostMapping("/{id}/close")
    public PurchaseRequestResponse close(@PathVariable Long id,
            @RequestBody(required = false) PurchaseDecisionRequest body) {
        return requests.close(id, body == null ? null : body.reason());
    }
}
