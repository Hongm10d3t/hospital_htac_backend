package com.htac_backend.modules.procurement.dto;

import com.htac_backend.modules.procurement.entity.PurchaseRequest;
import com.htac_backend.modules.procurement.entity.PurchaseRequestItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record PurchaseRequestResponse(Long id, String requestCode, Long supplierId, String status,
        Long createdBy, Instant createdAt, Long reviewedBy, Instant reviewedAt, String reviewNote,
        Instant orderedAt, LocalDate expectedDeliveryDate, Instant closedAt, String closeNote,
        String note, List<PurchaseRequestLineResponse> items) {
    public static PurchaseRequestResponse from(PurchaseRequest request,
            List<PurchaseRequestItem> items, Map<Long, BigDecimal> received) {
        return new PurchaseRequestResponse(request.getId(), request.getRequestCode(),
                request.getSupplierId(), request.getStatus(), request.getCreatedBy(),
                request.getCreatedAt(), request.getReviewedBy(), request.getReviewedAt(),
                request.getReviewNote(), request.getOrderedAt(), request.getExpectedDeliveryDate(),
                request.getClosedAt(), request.getCloseNote(), request.getNote(),
                items.stream().map(item -> {
                    BigDecimal amount = received.getOrDefault(item.getMedicineId(), BigDecimal.ZERO);
                    return new PurchaseRequestLineResponse(item.getId(), item.getMedicineId(),
                            item.getQuantity(), amount, item.getQuantity().subtract(amount), item.getNote());
                }).toList());
    }
}
