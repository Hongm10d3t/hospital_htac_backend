package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.pharmacy.dto.MedicineLabel;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M06-owned medicine check; catalog writes must use the same medicine row lock. */
@Service
public class PrescribableMedicineService {
    private final NamedParameterJdbcTemplate jdbc;

    public PrescribableMedicineService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, MedicineLabel> requireActive(Collection<Long> medicineIds) {
        List<Long> ids = medicineIds.stream().distinct().sorted().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        record Row(MedicineLabel label, boolean active) { }
        Map<Long, Row> found = jdbc.query("SELECT id, name, base_unit, is_active FROM medicine "
                        + "WHERE id IN (:ids) ORDER BY id FOR UPDATE", Map.of("ids", ids),
                (rs, row) -> new Row(new MedicineLabel(rs.getLong("id"), rs.getString("name"),
                        rs.getString("base_unit")), rs.getBoolean("is_active"))).stream()
                .collect(Collectors.toMap(row -> row.label().id(), Function.identity()));
        if (found.size() != ids.size()) {
            throw new NotFoundException("Không tìm thấy thuốc trong danh mục");
        }
        if (found.values().stream().anyMatch(row -> !row.active())) {
            throw new BusinessException("MEDICINE_INACTIVE", "Thuốc đã ngừng sử dụng");
        }
        return found.values().stream().map(Row::label)
                .collect(Collectors.toMap(MedicineLabel::id, Function.identity()));
    }
}
