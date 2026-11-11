package com.htac_backend.modules.reporting.dto;

import com.htac_backend.shared.api.PageResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ReportPage<T>(LocalDate from, LocalDate to, String timeBasis,
        Map<String, Object> filters,
        List<T> items, int page, int size, long totalItems, int totalPages) {
    public static <T> ReportPage<T> of(LocalDate from, LocalDate to, String timeBasis,
            PageResponse<T> data) {
        return of(from, to, timeBasis, Map.of(), data);
    }

    public static <T> ReportPage<T> of(LocalDate from, LocalDate to, String timeBasis,
            Map<String, Object> filters, PageResponse<T> data) {
        return new ReportPage<>(from, to, timeBasis, Map.copyOf(filters),
                data.items(), data.page(), data.size(),
                data.totalItems(), data.totalPages());
    }
}
