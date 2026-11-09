package com.htac_backend.modules.administration.service;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only cross-module security guard. Tables remain owned by their respective modules. */
@Service
public class AccountRoleHistoryService {
    private static final List<String> AUDITED_TABLES = List.of(
            "user_account", "patient", "specialty", "clinic_room", "doctor_profile", "clinic_slot",
            "appointment", "medical_visit", "prescription", "prescription_item", "medicine",
            "medicine_batch", "supplier", "stock_document", "stock_document_item",
            "purchase_request", "purchase_request_item", "hospital_config");
    private static final List<String> DIRECT_LINKS = List.of(
            "patient:user_id", "doctor_profile:user_id", "appointment:confirmed_by",
            "purchase_request:reviewed_by", "stock_document:posted_by");

    private final JdbcTemplate jdbc;

    public AccountRoleHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean hasLinkedData(Long accountId) {
        for (String table : AUDITED_TABLES) {
            if (exists("SELECT EXISTS(SELECT 1 FROM " + table
                    + " WHERE created_by = ? OR updated_by = ?)", accountId, accountId)) {
                return true;
            }
        }
        for (String link : DIRECT_LINKS) {
            String[] parts = link.split(":");
            if (exists("SELECT EXISTS(SELECT 1 FROM " + parts[0] + " WHERE " + parts[1] + " = ?)", accountId)) {
                return true;
            }
        }
        return false;
    }

    private boolean exists(String sql, Object... parameters) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, parameters));
    }
}
