package com.htac_backend.modules.pharmacy.dto;

import com.htac_backend.modules.pharmacy.entity.StockDocument;
import com.htac_backend.modules.pharmacy.entity.StockDocumentItem;
import java.time.Instant;
import java.util.List;

public record StockDocumentResponse(Long id, String documentCode, String type,
        Instant occurredAt, String status, String stockLocation, String stockBucket,
        String destinationLocation, String destinationBucket, Long supplierId,
        Long prescriptionId, Long purchaseRequestId, Instant postedAt, Long postedBy,
        String note, List<StockDocumentItemResponse> items) {
    public static StockDocumentResponse from(StockDocument document, List<StockDocumentItem> items) {
        return new StockDocumentResponse(document.getId(), document.getDocumentCode(),
                document.getType(), document.getOccurredAt(), document.getStatus(),
                document.getStockLocation(), document.getStockBucket(),
                document.getDestinationLocation(), document.getDestinationBucket(),
                document.getSupplierId(), document.getPrescriptionId(),
                document.getPurchaseRequestId(), document.getPostedAt(), document.getPostedBy(),
                document.getNote(), items.stream().map(StockDocumentItemResponse::from).toList());
    }
}
