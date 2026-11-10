package com.htac_backend.modules.pharmacy.dto;

import java.util.List;
import java.time.Instant;

public record DispensingSnapshotResponse(Long prescriptionId, Instant dispensingCheckedAt,
        boolean reviewed,
        List<DispensingLineResponse> items) { }
