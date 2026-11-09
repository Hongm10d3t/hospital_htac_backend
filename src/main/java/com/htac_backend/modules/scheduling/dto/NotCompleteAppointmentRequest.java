package com.htac_backend.modules.scheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotCompleteAppointmentRequest(@NotBlank @Size(max = 3000) String reason) { }
