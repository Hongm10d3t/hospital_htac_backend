package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.pharmacy.dto.CancelStockDocumentRequest;
import com.htac_backend.modules.pharmacy.dto.StockDocumentResponse;
import com.htac_backend.modules.pharmacy.service.StockDocumentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacy/stock-documents")
public class StockDocumentController {
    private final StockDocumentService documents;

    public StockDocumentController(StockDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/{id}")
    public StockDocumentResponse get(@PathVariable Long id) {
        return documents.get(id);
    }

    @PostMapping("/{id}/post")
    public StockDocumentResponse post(@PathVariable Long id) {
        return documents.post(id);
    }

    @PostMapping("/{id}/cancel")
    public StockDocumentResponse cancel(@PathVariable Long id,
            @Valid @RequestBody CancelStockDocumentRequest body) {
        return documents.cancel(id, body.reason());
    }
}
