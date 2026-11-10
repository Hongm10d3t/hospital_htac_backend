package com.htac_backend.modules.pharmacy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReviewUnfilledRequest(@NotEmpty List<@Valid UnfilledLineRequest> items) { }
