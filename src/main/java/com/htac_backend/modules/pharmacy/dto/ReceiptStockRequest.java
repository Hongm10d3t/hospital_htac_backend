package com.htac_backend.modules.pharmacy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record ReceiptStockRequest(@NotNull OffsetDateTime occurredAt,
        @NotBlank String stockLocation, @NotBlank String stockBucket,
        @NotNull Long supplierId, Long purchaseRequestId, String note,
        @NotEmpty List<@Valid InboundStockLineRequest> items) {
    public ReceiptStockRequest(OffsetDateTime occurredAt, String stockLocation, String stockBucket,
            Long supplierId, String note, List<InboundStockLineRequest> items) {
        this(occurredAt, stockLocation, stockBucket, supplierId, null, note, items);
    }
}
