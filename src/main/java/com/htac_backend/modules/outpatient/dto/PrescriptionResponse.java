package com.htac_backend.modules.outpatient.dto;

import com.htac_backend.modules.outpatient.entity.PrescriptionStatus;
import java.time.Instant;
import java.util.List;

public record PrescriptionResponse(Long id, String prescriptionCode, Long visitId, Long doctorId,
        PrescriptionStatus status, Instant issuedAt, String note, Instant dispensingCheckedAt,
        List<PrescriptionLineResponse> items) { }
