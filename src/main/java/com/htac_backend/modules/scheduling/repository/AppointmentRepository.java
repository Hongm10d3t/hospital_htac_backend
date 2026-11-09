package com.htac_backend.modules.scheduling.repository;

import com.htac_backend.modules.scheduling.entity.Appointment;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    boolean existsBySlotId(Long slotId);
    boolean existsByPatientIdAndSlotId(Long patientId, Long slotId);
    Optional<Appointment> findByIdAndPatientId(Long id, Long patientId);
    Page<Appointment> findByPatientId(Long patientId, Pageable pageable);

    @Query("select appointment.slotId from Appointment appointment where appointment.id = :id")
    Optional<Long> findSlotIdById(@Param("id") Long id);

    @Query("select appointment from Appointment appointment join ClinicSlot slot "
            + "on slot.id = appointment.slotId where (:status is null or appointment.status = :status) "
            + "and (:from is null or slot.startAt >= :from) and (:to is null or slot.startAt < :to)")
    Page<Appointment> searchReception(@Param("status") com.htac_backend.modules.scheduling.entity.AppointmentStatus status,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appointment from Appointment appointment where appointment.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @Query("select appointment.id from Appointment appointment where appointment.slotId = :slotId "
            + "and appointment.status in (com.htac_backend.modules.scheduling.entity.AppointmentStatus.PENDING, "
            + "com.htac_backend.modules.scheduling.entity.AppointmentStatus.CONFIRMED)")
    List<Long> findHoldingIds(@Param("slotId") Long slotId);
}
