package com.htac_backend.shared.api;

import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageQueryTest {
    @Test
    void appliesDefaultsAndParsesAllowedSort() {
        PageQuery defaults = PageQuery.parse(null, null, null, "id", Set.of("id", "name"));
        assertEquals(0, defaults.page());
        assertEquals(20, defaults.size());
        PageQuery requested = PageQuery.parse(2, 100, "name,desc", "id", Set.of("id", "name"));
        assertEquals(Sort.Direction.DESC, requested.toPageRequest().getSort().getOrderFor("name").getDirection());
    }

    @Test
    void rejectsOutOfRangeAndUnapprovedFields() {
        assertThrows(InvalidRequestException.class, () -> PageQuery.parse(-1, 20, null, "id", Set.of("id")));
        assertThrows(InvalidRequestException.class, () -> PageQuery.parse(0, 101, null, "id", Set.of("id")));
        assertThrows(InvalidRequestException.class, () -> PageQuery.parse(0, 20, "secret,asc", "id", Set.of("id")));
        assertThrows(InvalidRequestException.class, () -> PageQuery.parse(0, 20, "id,sideways", "id", Set.of("id")));
    }

    @Test
    void rejectsUnsupportedFilterAndPageBeyondResults() {
        assertThrows(InvalidRequestException.class,
                () -> FilterQuery.parse(Map.of("secret", "x"), Set.of("status")));
        assertThrows(InvalidRequestException.class,
                () -> FilterQuery.parse(Map.of("from", "2026-11-08", "to", "2026-11-07"),
                        Set.of("from", "to")));
        var emptyPage = new PageImpl<String>(java.util.List.of(), PageRequest.of(3, 20), 2);
        assertThrows(InvalidRequestException.class, () -> PageResponse.from(emptyPage));
    }
}
