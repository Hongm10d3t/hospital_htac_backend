package com.htac_backend.modules.reporting.dto;

import java.time.Instant;
import java.util.List;

public record PrescriptionReportDetail(Long id, String prescriptionCode, Instant issuedAt,
        Instant dispensingCheckedAt, boolean reviewed, List<PrescriptionReportItem> items) { }
