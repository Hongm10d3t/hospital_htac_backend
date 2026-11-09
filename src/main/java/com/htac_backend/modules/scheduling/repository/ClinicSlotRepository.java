package com.htac_backend.modules.scheduling.repository;

import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import com.htac_backend.modules.scheduling.dto.SlotAssignmentIds;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicSlotRepository extends JpaRepository<ClinicSlot, Long> {
    @Query("select new com.htac_backend.modules.scheduling.dto.SlotAssignmentIds(slot.doctorId, slot.roomId) "
            + "from ClinicSlot slot where slot.id = :id")
    Optional<SlotAssignmentIds> findAssignmentIds(@Param("id") Long id);

    @Query("select slot from ClinicSlot slot where (:roomId is null or slot.roomId = :roomId) "
            + "and (:doctorId is null or slot.doctorId = :doctorId) "
            + "and (:from is null or slot.startAt >= :from) "
            + "and (:to is null or slot.startAt < :to)")
    Page<ClinicSlot> search(@Param("roomId") Long roomId, @Param("doctorId") Long doctorId,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("select count(slot) from ClinicSlot slot where slot.startAt < :endAt and slot.endAt > :startAt "
            + "and (slot.roomId = :roomId or slot.doctorId = :doctorId) and (:excludedId is null or slot.id <> :excludedId)")
    long countOverlaps(@Param("roomId") Long roomId, @Param("doctorId") Long doctorId,
            @Param("startAt") Instant startAt, @Param("endAt") Instant endAt,
            @Param("excludedId") Long excludedId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from ClinicSlot slot where slot.id = :id")
    Optional<ClinicSlot> findByIdForUpdate(@Param("id") Long id);
}
