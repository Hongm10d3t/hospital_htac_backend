package com.htac_backend.modules.reporting.service;

import com.htac_backend.shared.api.InvalidRequestException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** The hospital's half-open local-day range, bound to UTC DATETIME columns. */
public record ReportWindow(LocalDate from, LocalDate to, Instant start, Instant end) {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static ReportWindow of(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new InvalidRequestException("Khoảng ngày báo cáo không hợp lệ");
        }
        return new ReportWindow(from, to, from.atStartOfDay(HOSPITAL_ZONE).toInstant(),
                to.atStartOfDay(HOSPITAL_ZONE).toInstant());
    }

    public Timestamp startSql() { return Timestamp.from(start); }
    public Timestamp endSql() { return Timestamp.from(end); }
}
