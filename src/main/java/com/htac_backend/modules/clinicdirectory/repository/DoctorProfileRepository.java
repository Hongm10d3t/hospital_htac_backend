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

    @Query("select profile from DoctorProfile profile where profile.publicProfile = true "
            + "and (:specialtyId is null or profile.specialtyId = :specialtyId) "
            + "and exists (select account.id from UserAccount account where account.id = profile.userId "
            + "and account.active = true and account.role = com.htac_backend.modules.administration.entity.AccountRole.DOCTOR) "
            + "and exists (select specialty.id from Specialty specialty where specialty.id = profile.specialtyId "
            + "and specialty.active = true)")
    Page<DoctorProfile> searchPublic(@Param("specialtyId") Long specialtyId, Pageable pageable);

    @Query("select profile from DoctorProfile profile where profile.userId = :userId "
            + "and profile.publicProfile = true "
            + "and exists (select account.id from UserAccount account where account.id = profile.userId "
            + "and account.active = true and account.role = com.htac_backend.modules.administration.entity.AccountRole.DOCTOR) "
            + "and exists (select specialty.id from Specialty specialty where specialty.id = profile.specialtyId "
            + "and specialty.active = true)")
    Optional<DoctorProfile> findPublicByUserId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from DoctorProfile profile where profile.userId = :userId")
    Optional<DoctorProfile> findByIdForUpdate(@Param("userId") Long userId);
}
