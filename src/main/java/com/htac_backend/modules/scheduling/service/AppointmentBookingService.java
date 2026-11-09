package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.administration.service.HospitalConfigService;
import com.htac_backend.modules.clinicdirectory.dto.ActiveClinicRoom;
import com.htac_backend.modules.clinicdirectory.service.ClinicDirectoryLookupService;
import com.htac_backend.modules.clinicdirectory.service.DoctorPublicService;
import com.htac_backend.modules.reception.service.PatientAccountLookupService;
import com.htac_backend.modules.scheduling.dto.AppointmentResponse;
import com.htac_backend.modules.scheduling.dto.BookAppointmentRequest;
import com.htac_backend.modules.scheduling.entity.Appointment;
import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import com.htac_backend.modules.scheduling.repository.AppointmentRepository;
import com.htac_backend.modules.scheduling.repository.ClinicSlotRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class AppointmentBookingService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CurrentSchedulingActor actor;
    private final AccountRoleLookupService accounts;
    private final PatientAccountLookupService patients;
    private final HospitalConfigService config;
    private final DoctorPublicService doctors;
    private final ClinicDirectoryLookupService directory;
    private final ClinicSlotRepository slots;
    private final AppointmentRepository appointments;
    private final SlotCapacityService capacity;

    public AppointmentBookingService(CurrentSchedulingActor actor, AccountRoleLookupService accounts,
            PatientAccountLookupService patients, HospitalConfigService config, DoctorPublicService doctors,
            ClinicDirectoryLookupService directory, ClinicSlotRepository slots,
            AppointmentRepository appointments, SlotCapacityService capacity) {
        this.actor = actor;
        this.accounts = accounts;
        this.patients = patients;
        this.config = config;
        this.doctors = doctors;
        this.directory = directory;
        this.slots = slots;
        this.appointments = appointments;
        this.capacity = capacity;
    }

    @PreAuthorize("hasRole('PATIENT')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse book(BookAppointmentRequest request) {
        Long accountId = actor.userId();
        if (!accounts.hasActiveRole(accountId, AccountRole.PATIENT)) {
            throw new BusinessException("PATIENT_ACCOUNT_INACTIVE", "Tài khoản bệnh nhân không hoạt động");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new InvalidRequestException("Lý do khám không được để trống");
        }
        Long patientId = patients.requirePatientId(accountId);
        ClinicSlot slot = slots.findByIdForUpdate(request.slotId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy khung khám"));
        if (appointments.existsByPatientIdAndSlotId(patientId, slot.getId())) {
            throw new BusinessException("APPOINTMENT_ALREADY_EXISTS",
                    "Bệnh nhân đã có lịch hẹn tại khung này");
        }
        Instant now = Instant.now();
        LocalDate hospitalDate = now.atZone(HOSPITAL_ZONE).toLocalDate();
        LocalDate slotDate = slot.getStartAt().atZone(HOSPITAL_ZONE).toLocalDate();
        if (!slot.getStartAt().isAfter(now) || !config.isWithinAdvanceWindow(slotDate, hospitalDate)) {
            throw new BusinessException("SLOT_OUTSIDE_BOOKING_WINDOW", "Khung khám ngoài thời hạn đặt trước");
        }
        var doctor = doctors.get(slot.getDoctorId());
        ActiveClinicRoom room = directory.requireActiveRoom(slot.getRoomId());
        if (!doctor.specialtyId().equals(room.specialtyId())) {
            throw new BusinessException("SPECIALTY_MISMATCH", "Bác sĩ và phòng khác chuyên khoa");
        }
        capacity.requireOpenSlotWithSpace(slot.getId());
        String code = "A" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        Appointment created = appointments.saveAndFlush(new Appointment(code, patientId, slot.getId(),
                request.reason().trim()));
        return AppointmentResponse.from(created, slot);
    }
}
