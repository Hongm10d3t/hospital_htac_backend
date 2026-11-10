package com.htac_backend.modules.outpatient.dto;

import java.time.Instant;
import java.util.List;

public record IssuedPrescriptionSnapshot(Long id, Long visitId, Long doctorId, Instant issuedAt,
        List<PrescriptionLineResponse> items) { }
