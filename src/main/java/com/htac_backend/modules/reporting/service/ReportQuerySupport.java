package com.htac_backend.modules.reporting.service;

import com.htac_backend.security.AuthenticatedActor;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ReportQuerySupport {
    private final NamedParameterJdbcTemplate jdbc;

    public ReportQuerySupport(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Long actorId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new AuthenticationCredentialsNotFoundException("Cần đăng nhập");
        }
        return actor.userId();
    }

    public <T> PageResponse<T> page(String groupedSql, MapSqlParameterSource parameters,
            String orderColumn, String tieBreakers, PageQuery page, RowMapper<T> mapper) {
        Long totalValue = jdbc.queryForObject("SELECT COUNT(*) FROM (" + groupedSql + ") report_count",
                parameters, Long.class);
        long total = totalValue == null ? 0 : totalValue;
        int totalPages = (int) ((total + page.size() - 1) / page.size());
        if (page.page() > 0 && page.page() >= totalPages) {
            throw new InvalidRequestException("Trang yêu cầu vượt phạm vi");
        }
        parameters.addValue("limit", page.size()).addValue("offset", (long) page.page() * page.size());
        String order = orderColumn + (page.direction().isAscending() ? " ASC" : " DESC")
                + ", " + tieBreakers;
        List<T> rows = jdbc.query(groupedSql + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
                parameters, mapper);
        return new PageResponse<>(rows, page.page(), page.size(), total, totalPages);
    }

    public NamedParameterJdbcTemplate jdbc() { return jdbc; }
}
