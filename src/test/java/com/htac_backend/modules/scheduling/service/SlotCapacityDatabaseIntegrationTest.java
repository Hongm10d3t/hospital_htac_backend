package com.htac_backend.modules.scheduling.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.clinicdirectory.dto.CreateClinicRoomRequest;
import com.htac_backend.modules.clinicdirectory.dto.CreateSpecialtyRequest;
import com.htac_backend.modules.clinicdirectory.service.ClinicRoomAdminService;
import com.htac_backend.modules.clinicdirectory.service.DoctorProfileCreationService;
import com.htac_backend.modules.clinicdirectory.service.SpecialtyAdminService;
import com.htac_backend.modules.reception.entity.Patient;
import com.htac_backend.modules.reception.entity.PatientGender;
import com.htac_backend.modules.reception.repository.PatientRepository;
import com.htac_backend.modules.scheduling.dto.CreateClinicSlotRequest;
import com.htac_backend.modules.scheduling.dto.UpdateClinicSlotRequest;
import com.htac_backend.modules.scheduling.entity.Appointment;
import com.htac_backend.modules.scheduling.repository.AppointmentRepository;
import com.htac_backend.modules.scheduling.repository.ClinicSlotRepository;
import com.htac_backend.shared.api.BusinessException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class SlotCapacityDatabaseIntegrationTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired ClinicSlotAdminService slots;
    @Autowired SlotCapacityService capacity;
    @Autowired AppointmentRepository appointments;
    @Autowired ClinicSlotRepository slotRepository;
    @Autowired PatientRepository patients;
    @Autowired SpecialtyAdminService specialties;
    @Autowired ClinicRoomAdminService rooms;
    @Autowired DoctorProfileCreationService doctors;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void sharedCapacityDoesNotDoubleCountReceivedAppointmentAndExcludesVoided() {
        Fixture fixture = fixture(2);
        Patient patient = patient();
        Appointment held = appointments.saveAndFlush(new Appointment(code("A"), patient.getId(),
                fixture.slotId(), "Checkup"));
        insertVisit(fixture, patient.getId(), null, 1, "WAITING");
        var slot = slots.get(fixture.slotId());
        assertEquals(2, capacity.occupancy(slotRepository.findById(slot.id()).orElseThrow()).occupied());
        assertEquals("SLOT_FULL", assertThrows(BusinessException.class,
                () -> capacity.requireOpenSlotWithSpace(fixture.slotId())).getCode());
        capacity.requireSpaceForReception(fixture.slotId(), held.getId());

        insertVisit(fixture, patient.getId(), held.getId(), 2, "NOT_COMPLETED");
        assertEquals(2, capacity.occupancy(slotRepository.findById(slot.id()).orElseThrow()).occupied());
        assertEquals("APPOINTMENT_NOT_HELD", assertThrows(BusinessException.class,
                () -> capacity.requireSpaceForReception(fixture.slotId(), held.getId())).getCode());
        jdbc.update("UPDATE medical_visit SET status = 'VOIDED', outcome_reason = 'entered in error' "
                + "WHERE slot_id = ? AND queue_number = 1", fixture.slotId());
        assertEquals(1, capacity.occupancy(slotRepository.findById(slot.id()).orElseThrow()).occupied());
        assertEquals(1, capacity.occupancy(slotRepository.findById(slot.id()).orElseThrow()).available());
        held.notComplete("Did not finish", Instant.now());
        appointments.flush();
        assertEquals(1, capacity.occupancy(slotRepository.findById(slot.id()).orElseThrow()).occupied());
        Appointment cancelled = appointments.saveAndFlush(new Appointment(code("A"), patient.getId(),
                fixture.slotId(), "Another checkup"));
        assertEquals(2, capacity.occupancy(slotRepository.findById(slot.id()).orElseThrow()).occupied());
        cancelled.notComplete("Cancelled before arrival", Instant.now());
        appointments.flush();
        assertEquals(1, capacity.occupancy(slotRepository.findById(slot.id()).orElseThrow()).occupied());
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void appointmentsAllowOnlyCapacityIncreaseAndClosingKeepsHistory() {
        Fixture fixture = fixture(2);
        Patient patient = patient();
        Appointment held = appointments.saveAndFlush(new Appointment(code("A"), patient.getId(),
                fixture.slotId(), "Checkup"));
        assertEquals("SLOT_HAS_APPOINTMENTS", assertThrows(BusinessException.class, () -> slots.update(
                fixture.slotId(), new UpdateClinicSlotRequest(fixture.start().plusMinutes(5),
                        fixture.start().plusMinutes(20), 2))).getCode());
        assertEquals(3, slots.update(fixture.slotId(), new UpdateClinicSlotRequest(fixture.start(),
                fixture.start().plusMinutes(15), 3)).capacity());
        slots.close(fixture.slotId());
        slots.close(fixture.slotId());
        assertEquals("CLOSED", slots.get(fixture.slotId()).status().name());
        assertEquals(held.getId(), appointments.findById(held.getId()).orElseThrow().getId());
        assertEquals("SLOT_CLOSED", assertThrows(BusinessException.class,
                () -> capacity.requireOpenSlotWithSpace(fixture.slotId())).getCode());
        capacity.requireSpaceForReception(fixture.slotId(), held.getId());
        insertVisit(fixture, patient.getId(), held.getId(), 1, "WAITING");
        assertEquals("SLOT_HAS_VISITS", assertThrows(BusinessException.class, () -> slots.update(
                fixture.slotId(), new UpdateClinicSlotRequest(fixture.start(),
                        fixture.start().plusMinutes(15), 4))).getCode());
    }

    private Fixture fixture(int maximum) {
        var specialty = specialties.create(new CreateSpecialtyRequest(code("s"), "Specialty"));
        var room = rooms.create(new CreateClinicRoomRequest(specialty.id(), code("r"), "Room", null));
        var doctor = accounts.saveAndFlush(new UserAccount(code("u"), encoder.encode(UUID.randomUUID().toString()),
                "Doctor", "0000000000", AccountRole.DOCTOR));
        doctors.create(doctor.getId(), specialty.id());
        OffsetDateTime start = LocalDate.now(ZONE).plusWeeks(2).atTime(8, 0).atZone(ZONE).toOffsetDateTime();
        var slot = slots.create(new CreateClinicSlotRequest(room.id(), doctor.getId(), start,
                start.plusMinutes(15), maximum));
        return new Fixture(slot.id(), room.id(), start);
    }

    private Patient patient() {
        return patients.saveAndFlush(new Patient(code("P"), null, null, "Test Patient",
                LocalDate.of(1990, 1, 1), PatientGender.OTHER, null));
    }

    private void insertVisit(Fixture fixture, Long patientId, Long appointmentId, int queueNumber,
            String status) {
        jdbc.update("INSERT INTO medical_visit (visit_code, patient_id, appointment_id, slot_id, room_id, "
                        + "queue_date, queue_number, arrived_at, status, outcome_reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", code("V"), patientId, appointmentId,
                fixture.slotId(), fixture.roomId(), fixture.start().toLocalDate(), queueNumber,
                Timestamp.from(Instant.now()), status,
                "NOT_COMPLETED".equals(status) ? "Patient left" : null);
    }

    private String code(String prefix) { return prefix + UUID.randomUUID().toString().substring(0, 10); }

    private record Fixture(Long slotId, Long roomId, OffsetDateTime start) { }
}
