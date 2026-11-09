package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.clinicdirectory.service.SlotAssignmentLockService;
import com.htac_backend.modules.scheduling.dto.ClinicSlotResponse;
import com.htac_backend.modules.scheduling.dto.CreateClinicSessionRequest;
import com.htac_backend.modules.scheduling.dto.CreateClinicSlotRequest;
import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import com.htac_backend.modules.scheduling.repository.ClinicSlotRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class ClinicSlotAdminService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ClinicSlotRepository slots;
    private final SlotAssignmentLockService assignments;

    public ClinicSlotAdminService(ClinicSlotRepository slots, SlotAssignmentLockService assignments) {
        this.slots = slots;
        this.assignments = assignments;
    }

    @Transactional
    public ClinicSlotResponse create(CreateClinicSlotRequest request) {
        Instant start = request.startAt().toInstant();
        Instant end = request.endAt().toInstant();
        return createAll(request.roomId(), request.doctorId(), request.capacity(),
                List.of(new TimeRange(start, end))).getFirst();
    }

    @Transactional
    public List<ClinicSlotResponse> createSession(CreateClinicSessionRequest request) {
        if (request.durationMinutes() < 1 || request.durationMinutes() > 240
                || request.startTime().getSecond() != 0 || request.endTime().getSecond() != 0
                || request.startTime().getNano() != 0 || request.endTime().getNano() != 0
                || !request.endTime().isAfter(request.startTime())) {
            throw new InvalidRequestException("Khoảng giờ hoặc thời lượng khung không hợp lệ");
        }
        long minutes = Duration.between(request.startTime(), request.endTime()).toMinutes();
        if (minutes % request.durationMinutes() != 0) {
            throw new InvalidRequestException("Khoảng giờ phải chia hết cho thời lượng khung");
        }
        List<LocalDate> dates = request.dates().stream().sorted().toList();
        if (new HashSet<>(dates).size() != dates.size()) {
            throw new InvalidRequestException("Ngày lập lịch bị trùng");
        }
        LocalDate monday = dates.getFirst().with(DayOfWeek.MONDAY);
        List<TimeRange> ranges = new ArrayList<>();
        for (LocalDate date : dates) {
            if (!date.with(DayOfWeek.MONDAY).equals(monday)) {
                throw new InvalidRequestException("Các ngày phải thuộc cùng một tuần");
            }
            LocalDateTime start = date.atTime(request.startTime());
            for (int i = 0; i < minutes / request.durationMinutes(); i++) {
                LocalDateTime end = start.plusMinutes(request.durationMinutes());
                ranges.add(new TimeRange(start.atZone(HOSPITAL_ZONE).toInstant(),
                        end.atZone(HOSPITAL_ZONE).toInstant()));
                start = end;
            }
        }
        return createAll(request.roomId(), request.doctorId(), request.capacity(), ranges);
    }

    private List<ClinicSlotResponse> createAll(Long roomId, Long doctorId, int capacity,
            List<TimeRange> ranges) {
        if (capacity < 1 || ranges.isEmpty()) {
            throw new InvalidRequestException("Sức chứa hoặc danh sách khung không hợp lệ");
        }
        List<TimeRange> sorted = ranges.stream().sorted(Comparator.comparing(TimeRange::start)).toList();
        Instant now = Instant.now();
        TimeRange previous = null;
        for (TimeRange range : sorted) {
            if (!range.start().isAfter(now) || !range.end().isAfter(range.start())) {
                throw new InvalidRequestException("Khung khám phải nằm trong tương lai và có thời lượng dương");
            }
            if (previous != null && previous.end().isAfter(range.start())) {
                throw new InvalidRequestException("Các khung trong lô bị giao nhau");
            }
            previous = range;
        }
        assignments.requireForNewSlot(doctorId, roomId);
        for (TimeRange range : sorted) {
            if (slots.countOverlaps(roomId, doctorId, range.start(), range.end(), null) > 0) {
                throw new BusinessException("SLOT_OVERLAP", "Lịch bác sĩ hoặc phòng khám bị giao giờ");
            }
        }
        List<ClinicSlot> created = sorted.stream()
                .map(range -> new ClinicSlot(roomId, doctorId, range.start(), range.end(), capacity)).toList();
        return slots.saveAllAndFlush(created).stream().map(ClinicSlotResponse::from).toList();
    }

    private record TimeRange(Instant start, Instant end) { }
}
