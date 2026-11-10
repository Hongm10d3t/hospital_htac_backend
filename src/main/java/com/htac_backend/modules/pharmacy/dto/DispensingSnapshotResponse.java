package com.htac_backend.modules.pharmacy.dto;

import java.util.List;

public record DispensingSnapshotResponse(Long prescriptionId,
        List<DispensingLineResponse> items) { }
