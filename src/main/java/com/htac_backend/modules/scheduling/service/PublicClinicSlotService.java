package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.administration.service.HospitalConfigService;
import com.htac_backend.modules.scheduling.dto.PublicClinicSlotResponse;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicClinicSlotService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String CANDIDATES = """
            WITH candidates AS (
              SELECT slot.id, slot.room_id, room.name AS room_name, slot.doctor_id,
                     room.specialty_id, specialty.name AS specialty_name,
                     slot.start_at, slot.end_at, slot.capacity,
                     (SELECT COUNT(*) FROM medical_visit visit
                      WHERE visit.slot_id = slot.id AND visit.status <> 'VOIDED') AS actual_count,
                     (SELECT COUNT(*) FROM appointment appointment
                      WHERE appointment.slot_id = slot.id
                        AND appointment.status IN ('PENDING', 'CONFIRMED')
                        AND NOT EXISTS (
                          SELECT 1 FROM medical_visit received
                          WHERE received.appointment_id = appointment.id
                            AND received.status <> 'VOIDED')) AS holding_count
              FROM clinic_slot slot
              JOIN clinic_room room ON room.id = slot.room_id AND room.is_active = TRUE
              JOIN specialty specialty ON specialty.id = room.specialty_id AND specialty.is_active = TRUE
              JOIN doctor_profile profile ON profile.user_id = slot.doctor_id
                  AND profile.specialty_id = room.specialty_id AND profile.is_public = TRUE
              JOIN user_account account ON account.id = profile.user_id
                  AND account.is_active = TRUE AND account.role = 'DOCTOR'
              WHERE slot.status = 'OPEN' AND slot.start_at >= ? AND slot.start_at < ?
                AND (? IS NULL OR slot.doctor_id = ?)
                AND (? IS NULL OR room.specialty_id = ?)
            )
            """;
    private static final String VISIBLE = "FROM candidates WHERE capacity > actual_count + holding_count";
    private final JdbcTemplate jdbc;
    private final HospitalConfigService config;

    public PublicClinicSlotService(JdbcTemplate jdbc, HospitalConfigService config) {
        this.jdbc = jdbc;
        this.config = config;
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicClinicSlotResponse> search(LocalDate date, Long specialtyId, Long doctorId,
            PageQuery page) {
        if ((specialtyId != null && specialtyId < 1) || (doctorId != null && doctorId < 1)) {
            throw new InvalidRequestException("Bộ lọc lịch không hợp lệ");
        }
        Instant now = Instant.now();
        LocalDate today = now.atZone(HOSPITAL_ZONE).toLocalDate();
        if (date != null && !config.isWithinAdvanceWindow(date, today)) {
            throw new InvalidRequestException("Ngày tìm lịch ngoài thời hạn đặt trước");
        }
        Instant from = date == null ? now : date.atStartOfDay(HOSPITAL_ZONE).toInstant();
        if (from.isBefore(now)) {
            from = now;
        }
        Instant to = (date == null ? today.plusDays((long) config.maxAdvanceBookingDays() + 1)
                : date.plusDays(1)).atStartOfDay(HOSPITAL_ZONE).toInstant();
        Object[] filters = {Timestamp.from(from), Timestamp.from(to), doctorId, doctorId,
                specialtyId, specialtyId};
        long total = jdbc.queryForObject(CANDIDATES + "SELECT COUNT(*) " + VISIBLE,
                Long.class, filters);
        long totalPages = (total + page.size() - 1) / page.size();
        if (page.page() > 0 && page.page() >= totalPages) {
            throw new InvalidRequestException("Trang yêu cầu vượt phạm vi");
        }
        String order = page.direction().isAscending() ? "ASC" : "DESC";
        Object[] paged = {filters[0], filters[1], filters[2], filters[3], filters[4], filters[5],
                page.size(), (long) page.page() * page.size()};
        List<PublicClinicSlotResponse> items = jdbc.query(CANDIDATES + "SELECT id, room_id, room_name, "
                        + "doctor_id, specialty_id, specialty_name, start_at, end_at, capacity, "
                        + "capacity - actual_count - holding_count AS available " + VISIBLE
                        + " ORDER BY start_at " + order + ", id " + order + " LIMIT ? OFFSET ?",
                (result, row) -> new PublicClinicSlotResponse(result.getLong("id"),
                        result.getLong("room_id"), result.getString("room_name"),
                        result.getLong("doctor_id"), result.getLong("specialty_id"),
                        result.getString("specialty_name"), result.getTimestamp("start_at").toInstant(),
                        result.getTimestamp("end_at").toInstant(), result.getInt("capacity"),
                        result.getInt("available")), paged);
        return new PageResponse<>(items, page.page(), page.size(), total, Math.toIntExact(totalPages));
    }
}
