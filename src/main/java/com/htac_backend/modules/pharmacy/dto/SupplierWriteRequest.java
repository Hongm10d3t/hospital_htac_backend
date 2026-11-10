package com.htac_backend.modules.pharmacy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SupplierWriteRequest(@NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 200) String name, @Size(max = 20) String phone,
        @Size(max = 2000) String address, @NotNull Boolean active) { }
