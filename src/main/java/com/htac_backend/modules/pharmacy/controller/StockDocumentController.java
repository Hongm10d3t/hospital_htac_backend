package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.pharmacy.dto.CancelStockDocumentRequest;
import com.htac_backend.modules.pharmacy.dto.OpeningStockRequest;
import com.htac_backend.modules.pharmacy.dto.ReceiptStockRequest;
import com.htac_backend.modules.pharmacy.dto.StockDocumentResponse;
import com.htac_backend.modules.pharmacy.dto.TransferStockRequest;
import com.htac_backend.modules.pharmacy.dto.DispenseStockRequest;
import com.htac_backend.modules.pharmacy.dto.PatientReturnRequest;
import com.htac_backend.modules.pharmacy.service.InboundStockService;
import com.htac_backend.modules.pharmacy.service.DispenseStockService;
import com.htac_backend.modules.pharmacy.service.PatientReturnService;
import com.htac_backend.modules.pharmacy.service.TransferStockService;
import com.htac_backend.modules.pharmacy.service.StockDocumentService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
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
    private final InboundStockService inbound;
    private final TransferStockService transfers;
    private final DispenseStockService dispensing;
    private final PatientReturnService patientReturns;

    public StockDocumentController(StockDocumentService documents, InboundStockService inbound,
            TransferStockService transfers, DispenseStockService dispensing,
            PatientReturnService patientReturns) {
        this.documents = documents;
        this.inbound = inbound;
        this.transfers = transfers;
        this.dispensing = dispensing;
        this.patientReturns = patientReturns;
    }

    @PostMapping("/openings")
    public ResponseEntity<StockDocumentResponse> opening(@Valid @RequestBody OpeningStockRequest body) {
        StockDocumentResponse created = inbound.opening(body);
        return ResponseEntity.created(URI.create("/api/v1/pharmacy/stock-documents/" + created.id()))
                .body(created);
    }

    @PostMapping("/receipts")
    public ResponseEntity<StockDocumentResponse> receipt(@Valid @RequestBody ReceiptStockRequest body) {
        StockDocumentResponse created = inbound.receipt(body);
        return ResponseEntity.created(URI.create("/api/v1/pharmacy/stock-documents/" + created.id()))
                .body(created);
    }

    @PostMapping("/transfers")
    public ResponseEntity<StockDocumentResponse> transfer(@Valid @RequestBody TransferStockRequest body) {
        StockDocumentResponse created = transfers.create(body);
        return ResponseEntity.created(URI.create("/api/v1/pharmacy/stock-documents/" + created.id()))
                .body(created);
    }

    @PostMapping("/dispensations")
    public ResponseEntity<StockDocumentResponse> dispense(@Valid @RequestBody DispenseStockRequest body) {
        StockDocumentResponse created = dispensing.create(body);
        return ResponseEntity.created(URI.create("/api/v1/pharmacy/stock-documents/" + created.id()))
                .body(created);
    }

    @PostMapping("/patient-returns")
    public ResponseEntity<StockDocumentResponse> patientReturn(
            @Valid @RequestBody PatientReturnRequest body) {
        StockDocumentResponse created = patientReturns.create(body);
        return ResponseEntity.created(URI.create("/api/v1/pharmacy/stock-documents/" + created.id()))
                .body(created);
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
