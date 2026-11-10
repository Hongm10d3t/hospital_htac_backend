package com.htac_backend.modules.outpatient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelPrescriptionRequest(@NotBlank @Size(max = 3000) String reason) { }
