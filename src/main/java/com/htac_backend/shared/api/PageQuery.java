package com.htac_backend.shared.api;

import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Validated paging parameters; each endpoint supplies its own allowed sort fields. */
public record PageQuery(int page, int size, String sortBy, Sort.Direction direction) {
    public static PageQuery parse(Integer page, Integer size, String sort,
                                  String defaultSortBy, Set<String> allowedSortFields) {
        int requestedPage = page == null ? 0 : page;
        int requestedSize = size == null ? 20 : size;
        if (requestedPage < 0 || requestedSize < 1 || requestedSize > 100) {
            throw new InvalidRequestException("page hoặc size không hợp lệ");
        }
        String field = defaultSortBy;
        Sort.Direction direction = Sort.Direction.ASC;
        if (sort != null) {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new InvalidRequestException("sort không hợp lệ");
            }
            field = parts[0];
            try {
                direction = Sort.Direction.valueOf(parts[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new InvalidRequestException("sort không hợp lệ");
            }
        }
        if (!allowedSortFields.contains(field)) {
            throw new InvalidRequestException("Trường sắp xếp không được hỗ trợ");
        }
        return new PageQuery(requestedPage, requestedSize, field, direction);
    }

    public PageRequest toPageRequest() {
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }
}
