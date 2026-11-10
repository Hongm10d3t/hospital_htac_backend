package com.htac_backend.modules.outpatient.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePrescriptionRequest(@Size(max = 3000) String note,
        @NotEmpty List<@Valid PrescriptionLineRequest> items) { }
