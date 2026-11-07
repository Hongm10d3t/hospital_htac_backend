package com.htac_backend.shared.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> result) {
        if (result.getNumber() > 0 && result.getNumber() >= result.getTotalPages()) {
            throw new InvalidRequestException("Trang yêu cầu vượt phạm vi");
        }
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
