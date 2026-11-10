package com.htac_backend.modules.reception.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReceptionOutcomeRequest(@NotBlank @Size(max = 3000) String reason) { }
