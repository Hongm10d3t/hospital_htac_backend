package com.htac_backend.modules.clinicdirectory.repository;

import com.htac_backend.modules.clinicdirectory.entity.Specialty;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
    Page<Specialty> findByActive(boolean active, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select specialty from Specialty specialty where specialty.id = :id")
    Optional<Specialty> findByIdForUpdate(@Param("id") Long id);
}
