package com.htac_backend.modules.procurement.dto;

import java.math.BigDecimal;

public record PurchaseRequestLineResponse(Long id, Long medicineId, BigDecimal quantity,
        BigDecimal receivedQuantity, BigDecimal remainingQuantity, String note) { }
