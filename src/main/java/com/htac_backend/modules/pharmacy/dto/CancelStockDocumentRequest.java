package com.htac_backend.modules.pharmacy.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelStockDocumentRequest(@NotBlank String reason) { }
