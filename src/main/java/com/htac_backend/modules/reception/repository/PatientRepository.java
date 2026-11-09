package com.htac_backend.modules.reception.repository;

import com.htac_backend.modules.reception.entity.Patient;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    boolean existsByFullNameIgnoreCaseAndDateOfBirth(String fullName, LocalDate dateOfBirth);
    boolean existsByCitizenId(String citizenId);
    Optional<Patient> findByUserId(Long userId);
    Page<Patient> findByCitizenId(String citizenId, Pageable pageable);
    Page<Patient> findByFullNameIgnoreCaseAndDateOfBirth(String fullName, LocalDate dateOfBirth,
                                                         Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select patient from Patient patient where patient.citizenId = :citizenId")
    Optional<Patient> findByCitizenIdForUpdate(@Param("citizenId") String citizenId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select patient from Patient patient where patient.id = :id")
    Optional<Patient> findByIdForUpdate(@Param("id") Long id);
}
