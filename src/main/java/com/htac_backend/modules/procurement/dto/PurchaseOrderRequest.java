package com.htac_backend.modules.procurement.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PurchaseOrderRequest(@NotNull OffsetDateTime orderedAt,
        @NotNull LocalDate expectedDeliveryDate) { }
