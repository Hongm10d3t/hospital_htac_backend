package com.htac_backend.shared.api;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/** Shared guard for list filters; modules validate the meaning of each approved value. */
public record FilterQuery(Map<String, String> values) {
    public static FilterQuery parse(Map<String, String> requested, Set<String> allowedFields) {
        for (var entry : requested.entrySet()) {
            if (!allowedFields.contains(entry.getKey()) || entry.getValue() == null
                    || entry.getValue().isBlank()) {
                throw new InvalidRequestException("Bộ lọc không hợp lệ");
            }
        }
        if (requested.containsKey("from") || requested.containsKey("to")) {
            try {
                LocalDate from = requested.containsKey("from") ? LocalDate.parse(requested.get("from")) : null;
                LocalDate to = requested.containsKey("to") ? LocalDate.parse(requested.get("to")) : null;
                if (from != null && to != null && !to.isAfter(from)) {
                    throw new InvalidRequestException("Khoảng ngày không hợp lệ");
                }
            } catch (DateTimeParseException exception) {
                throw new InvalidRequestException("Ngày lọc không hợp lệ");
            }
        }
        return new FilterQuery(Map.copyOf(requested));
    }
}
