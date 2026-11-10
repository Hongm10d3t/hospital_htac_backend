package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.pharmacy.dto.MedicineLabel;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M06-owned read projection for historical prescription display. */
@Service
public class MedicineDisplayLookupService {
    private final NamedParameterJdbcTemplate jdbc;

    public MedicineDisplayLookupService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Map<Long, MedicineLabel> labels(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.query("SELECT id, name, base_unit FROM medicine WHERE id IN (:ids)",
                Map.of("ids", ids), (rs, row) -> new MedicineLabel(rs.getLong("id"),
                        rs.getString("name"), rs.getString("base_unit"))).stream()
                .collect(Collectors.toMap(MedicineLabel::id, Function.identity()));
    }
}
