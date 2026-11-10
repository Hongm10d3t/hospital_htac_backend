package com.htac_backend.modules.outpatient.repository;

import com.htac_backend.modules.outpatient.entity.MedicalVisit;
import com.htac_backend.modules.outpatient.entity.VisitStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicalVisitRepository extends JpaRepository<MedicalVisit, Long> {
    Optional<MedicalVisit> findByAppointmentId(Long appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select visit from MedicalVisit visit where visit.id = :id")
    Optional<MedicalVisit> findByIdForUpdate(@Param("id") Long id);

    @Query("select coalesce(max(visit.queueNumber), 0) from MedicalVisit visit "
            + "where visit.roomId = :roomId and visit.queueDate = :queueDate")
    int maxQueueNumber(@Param("roomId") Long roomId, @Param("queueDate") LocalDate queueDate);

    @Query("select visit from MedicalVisit visit where visit.status = :status "
            + "and visit.queueDate = :queueDate and (:roomId is null or visit.roomId = :roomId) "
            + "and (:slotId is null or visit.slotId = :slotId)")
    Page<MedicalVisit> waiting(@Param("status") VisitStatus status, @Param("queueDate") LocalDate queueDate,
            @Param("roomId") Long roomId, @Param("slotId") Long slotId, Pageable pageable);
}
