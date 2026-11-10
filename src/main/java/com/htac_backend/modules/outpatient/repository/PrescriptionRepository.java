package com.htac_backend.modules.outpatient.repository;

import com.htac_backend.modules.outpatient.entity.Prescription;
import com.htac_backend.modules.outpatient.entity.PrescriptionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findByVisitIdOrderById(Long visitId);
    boolean existsByVisitIdAndStatus(Long visitId, PrescriptionStatus status);

    @Query("select prescription.visitId from Prescription prescription where prescription.id = :id")
    Optional<Long> findVisitIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select prescription from Prescription prescription where prescription.id = :id")
    Optional<Prescription> findByIdForUpdate(@Param("id") Long id);
}
