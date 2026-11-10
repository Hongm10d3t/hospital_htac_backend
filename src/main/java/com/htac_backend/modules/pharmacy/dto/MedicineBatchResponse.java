package com.htac_backend.modules.pharmacy.dto;

import com.htac_backend.modules.pharmacy.entity.MedicineBatch;
import java.time.LocalDate;

public record MedicineBatchResponse(Long id, Long medicineId, String medicineCode,
        String batchNumber, LocalDate expiryDate, String status, String blockReason) {
    public static MedicineBatchResponse from(MedicineBatch batch) {
        return new MedicineBatchResponse(batch.getId(), batch.getMedicine().getId(),
                batch.getMedicine().getCode(), batch.getBatchNumber(), batch.getExpiryDate(),
                batch.getStatus(), batch.getBlockReason());
    }
}
