package com.htac_backend.modules.pharmacy.dto;

import com.htac_backend.modules.pharmacy.entity.StockDocumentItem;
import java.math.BigDecimal;

public record StockDocumentItemResponse(Long id, Long batchId, BigDecimal quantity,
        BigDecimal unitCost, Long prescriptionItemId, Long sourceItemId,
        BigDecimal bookQuantity, BigDecimal countedQuantity, String note) {
    public static StockDocumentItemResponse from(StockDocumentItem item) {
        return new StockDocumentItemResponse(item.getId(), item.getBatch().getId(),
                item.getQuantity(), item.getUnitCost(), item.getPrescriptionItemId(),
                item.getSourceItemId(), item.getBookQuantity(), item.getCountedQuantity(),
                item.getNote());
    }
}
