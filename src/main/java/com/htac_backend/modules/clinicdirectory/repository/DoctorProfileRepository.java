package com.htac_backend.modules.clinicdirectory.repository;

import com.htac_backend.modules.clinicdirectory.entity.DoctorProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, Long> {
    @Query("select profile from DoctorProfile profile where :specialtyId is null "
            + "or profile.specialtyId = :specialtyId")
    Page<DoctorProfile> search(@Param("specialtyId") Long specialtyId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from DoctorProfile profile where profile.userId = :userId")
    Optional<DoctorProfile> findByIdForUpdate(@Param("userId") Long userId);
}
