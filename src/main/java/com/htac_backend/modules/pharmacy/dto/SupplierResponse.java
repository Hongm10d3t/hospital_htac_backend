package com.htac_backend.modules.pharmacy.dto;

import com.htac_backend.modules.pharmacy.entity.Supplier;

public record SupplierResponse(Long id, String code, String name, String phone,
        String address, boolean active) {
    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(supplier.getId(), supplier.getCode(), supplier.getName(),
                supplier.getPhone(), supplier.getAddress(), supplier.isActive());
    }
}
