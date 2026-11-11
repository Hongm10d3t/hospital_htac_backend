package com.htac_backend.modules.procurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PurchaseRequestWrite(Long supplierId, String note,
        @NotEmpty List<@Valid PurchaseRequestLineWrite> items) { }
