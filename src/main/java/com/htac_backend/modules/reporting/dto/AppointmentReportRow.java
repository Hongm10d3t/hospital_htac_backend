package com.htac_backend.modules.reporting.dto;

import java.time.LocalDate;

public record AppointmentReportRow(LocalDate date, String status, Long doctorId,
        Long roomId, long count) { }
