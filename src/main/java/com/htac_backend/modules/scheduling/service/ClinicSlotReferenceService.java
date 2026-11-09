package com.htac_backend.modules.scheduling.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only M03 contract used before changing a room's specialty. */
@Service
public class ClinicSlotReferenceService {
    private final JdbcTemplate jdbc;

    public ClinicSlotReferenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean roomHasSlots(Long roomId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM clinic_slot WHERE room_id = ?)", Boolean.class, roomId));
    }
}
