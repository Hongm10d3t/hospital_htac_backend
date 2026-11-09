package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.clinicdirectory.service.SlotAssignmentLockService;
import com.htac_backend.modules.scheduling.dto.ClinicSlotResponse;
import com.htac_backend.modules.scheduling.dto.CreateClinicSessionRequest;
import com.htac_backend.modules.scheduling.dto.CreateClinicSlotRequest;
import com.htac_backend.modules.scheduling.dto.UpdateClinicSlotRequest;
import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import com.htac_backend.modules.scheduling.repository.AppointmentRepository;
import com.htac_backend.modules.scheduling.repository.ClinicSlotRepository;
import com.htac_backend.modules.outpatient.service.VisitCapacityReadService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
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
import org.springframework.transaction.annotation.Isolation;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class ClinicSlotAdminService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ClinicSlotRepository slots;
    private final SlotAssignmentLockService assignments;
    private final AppointmentRepository appointments;
    private final VisitCapacityReadService visits;

    public ClinicSlotAdminService(ClinicSlotRepository slots, SlotAssignmentLockService assignments,
            AppointmentRepository appointments, VisitCapacityReadService visits) {
        this.slots = slots;
        this.assignments = assignments;
        this.appointments = appointments;
        this.visits = visits;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClinicSlotResponse> list(Long roomId, Long doctorId, Instant from, Instant to,
            PageQuery page) {
        if ((roomId != null && roomId < 1) || (doctorId != null && doctorId < 1)
                || (from != null && to != null && !to.isAfter(from))) {
            throw new InvalidRequestException("Bộ lọc lịch không hợp lệ");
        }
        return PageResponse.from(slots.search(roomId, doctorId, from, to, page.toPageRequest())
                .map(ClinicSlotResponse::from));
    }

    @Transactional(readOnly = true)
    public ClinicSlotResponse get(Long id) {
        return ClinicSlotResponse.from(slots.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khung khám")));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClinicSlotResponse update(Long id, UpdateClinicSlotRequest request) {
        var assignment = slots.findAssignmentIds(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khung khám"));
        assignments.requireForNewSlot(assignment.doctorId(), assignment.roomId());
        ClinicSlot slot = slots.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khung khám"));
        Instant start = request.startAt().toInstant();
        Instant end = request.endAt().toInstant();
        if (!start.isAfter(Instant.now()) || !end.isAfter(start)) {
            throw new InvalidRequestException("Khung khám phải ở tương lai và có thời lượng dương");
        }
        if (visits.hasAnyVisit(id)) {
            throw new BusinessException("SLOT_HAS_VISITS", "Khung đã có lượt thực tế, không thể điều chỉnh");
        }
        boolean sameTime = slot.getStartAt().equals(start) && slot.getEndAt().equals(end);
        if (appointments.existsBySlotId(id) && (!sameTime || request.capacity() < slot.getCapacity())) {
            throw new BusinessException("SLOT_HAS_APPOINTMENTS",
                    "Khung đã có lịch hẹn, chỉ có thể tăng sức chứa");
        }
        if (!sameTime && slots.countOverlaps(slot.getRoomId(), slot.getDoctorId(), start, end, id) > 0) {
            throw new BusinessException("SLOT_OVERLAP", "Lịch bác sĩ hoặc phòng khám bị giao giờ");
        }
        slot.replace(slot.getRoomId(), slot.getDoctorId(), start, end, request.capacity());
        slots.flush();
        return ClinicSlotResponse.from(slot);
    }

    @Transactional
    public void close(Long id) {
        ClinicSlot slot = slots.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khung khám"));
        slot.close();
        slots.flush();
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
