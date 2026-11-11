package com.htac_backend.modules.reporting.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.reporting.dto.AppointmentReportRow;
import com.htac_backend.modules.reporting.dto.ReportPage;
import com.htac_backend.modules.reporting.dto.VisitReportRow;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.PageQuery;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalReportService {
    private static final Set<String> APPOINTMENT_STATUSES = Set.of(
            "PENDING", "CONFIRMED", "COMPLETED", "NOT_COMPLETED");
    private static final Set<String> VISIT_STATUSES = Set.of(
            "WAITING", "IN_PROGRESS", "COMPLETED", "NOT_COMPLETED");
    private final ReportQuerySupport query;
    private final AccountRoleLookupService accounts;

    public ClinicalReportService(ReportQuerySupport query, AccountRoleLookupService accounts) {
        this.query = query;
        this.accounts = accounts;
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','DOCTOR')")
    @Transactional(readOnly = true)
    public ReportPage<AppointmentReportRow> appointments(LocalDate from, LocalDate to,
            Long doctorId, Long roomId, String status, PageQuery page) {
        ReportWindow window = ReportWindow.of(from, to);
        doctorId = scopedDoctor(doctorId);
        validId(roomId);
        if (status != null && !APPOINTMENT_STATUSES.contains(status)) {
            throw new InvalidRequestException("Trạng thái lịch hẹn không hợp lệ");
        }
        var args = timeArgs(window).addValue("doctorId", doctorId).addValue("roomId", roomId)
                .addValue("status", status);
        String sql = """
                SELECT DATE(DATE_ADD(s.start_at, INTERVAL 7 HOUR)) AS report_date,
                    a.status, s.doctor_id, s.room_id, COUNT(*) AS total_count
                FROM appointment a JOIN clinic_slot s ON s.id = a.slot_id
                WHERE s.start_at >= :start AND s.start_at < :end
                    AND (:doctorId IS NULL OR s.doctor_id = :doctorId)
                    AND (:roomId IS NULL OR s.room_id = :roomId)
                    AND (:status IS NULL OR a.status = :status)
                GROUP BY report_date, a.status, s.doctor_id, s.room_id
                """;
        var result = query.page(sql, args, sort(page),
                "report_date, doctor_id, room_id, status", page, (rs, number) ->
                        new AppointmentReportRow(rs.getDate("report_date").toLocalDate(),
                                rs.getString("status"), rs.getLong("doctor_id"),
                                rs.getLong("room_id"), rs.getLong("total_count")));
        return ReportPage.of(from, to, "clinic_slot.start_at", result);
    }

    @PreAuthorize("hasAnyRole('ADMIN','RECEPTIONIST','DOCTOR')")
    @Transactional(readOnly = true)
    public ReportPage<VisitReportRow> visits(LocalDate from, LocalDate to,
            Long doctorId, Long roomId, Long specialtyId, String status, PageQuery page) {
        ReportWindow window = ReportWindow.of(from, to);
        doctorId = scopedDoctor(doctorId);
        validId(roomId);
        validId(specialtyId);
        if (status != null && !VISIT_STATUSES.contains(status)) {
            throw new InvalidRequestException("Trạng thái lượt đến không hợp lệ");
        }
        var args = timeArgs(window).addValue("doctorId", doctorId).addValue("roomId", roomId)
                .addValue("specialtyId", specialtyId).addValue("status", status);
        String sql = """
                SELECT DATE(DATE_ADD(v.arrived_at, INTERVAL 7 HOUR)) AS report_date,
                    v.status, s.doctor_id, v.room_id, cr.specialty_id, COUNT(*) AS total_count
                FROM medical_visit v JOIN clinic_slot s ON s.id = v.slot_id
                    JOIN clinic_room cr ON cr.id = v.room_id
                WHERE v.arrived_at >= :start AND v.arrived_at < :end AND v.status <> 'VOIDED'
                    AND (:doctorId IS NULL OR s.doctor_id = :doctorId)
                    AND (:roomId IS NULL OR v.room_id = :roomId)
                    AND (:specialtyId IS NULL OR cr.specialty_id = :specialtyId)
                    AND (:status IS NULL OR v.status = :status)
                GROUP BY report_date, v.status, s.doctor_id, v.room_id, cr.specialty_id
                """;
        var result = query.page(sql, args, sort(page),
                "report_date, doctor_id, room_id, specialty_id, status", page, (rs, number) ->
                        new VisitReportRow(rs.getDate("report_date").toLocalDate(),
                                rs.getString("status"), rs.getLong("doctor_id"),
                                rs.getLong("room_id"), rs.getLong("specialty_id"),
                                rs.getLong("total_count")));
        return ReportPage.of(from, to, "medical_visit.arrived_at", result);
    }

    private MapSqlParameterSource timeArgs(ReportWindow window) {
        return new MapSqlParameterSource("start", window.startSql()).addValue("end", window.endSql());
    }

    private Long scopedDoctor(Long requested) {
        Long actor = query.actorId();
        if (accounts.hasActiveRole(actor, AccountRole.DOCTOR)) {
            if (requested != null && !requested.equals(actor)) {
                throw new AccessDeniedException("Chỉ xem báo cáo bác sĩ được phân công");
            }
            return actor;
        }
        if (!accounts.hasActiveRole(actor, AccountRole.ADMIN)
                && !accounts.hasActiveRole(actor, AccountRole.RECEPTIONIST)) {
            throw new AccessDeniedException("Không có quyền xem báo cáo khám");
        }
        validId(requested);
        return requested;
    }

    private void validId(Long id) {
        if (id != null && id <= 0) throw new InvalidRequestException("ID lọc không hợp lệ");
    }

    private String sort(PageQuery page) {
        return switch (page.sortBy()) {
            case "date" -> "report_date";
            case "count" -> "total_count";
            default -> throw new InvalidRequestException("Trường sắp xếp không hợp lệ");
        };
    }
}
