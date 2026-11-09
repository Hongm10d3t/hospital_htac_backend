package com.htac_backend.modules.scheduling.repository;

import com.htac_backend.modules.scheduling.entity.ClinicSlot;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicSlotRepository extends JpaRepository<ClinicSlot, Long> {
    @Query("select count(slot) from ClinicSlot slot where slot.startAt < :endAt and slot.endAt > :startAt "
            + "and (slot.roomId = :roomId or slot.doctorId = :doctorId) and (:excludedId is null or slot.id <> :excludedId)")
    long countOverlaps(@Param("roomId") Long roomId, @Param("doctorId") Long doctorId,
            @Param("startAt") Instant startAt, @Param("endAt") Instant endAt,
            @Param("excludedId") Long excludedId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from ClinicSlot slot where slot.id = :id")
    Optional<ClinicSlot> findByIdForUpdate(@Param("id") Long id);
}
