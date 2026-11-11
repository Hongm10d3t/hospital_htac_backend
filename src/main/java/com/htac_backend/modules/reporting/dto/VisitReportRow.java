package com.htac_backend.modules.reporting.dto;

import java.time.LocalDate;

public record VisitReportRow(LocalDate date, String status, Long doctorId,
        Long roomId, Long specialtyId, long count) { }
