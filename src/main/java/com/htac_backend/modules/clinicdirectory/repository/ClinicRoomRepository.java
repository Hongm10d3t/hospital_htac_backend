package com.htac_backend.modules.clinicdirectory.repository;

import com.htac_backend.modules.clinicdirectory.entity.ClinicRoom;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicRoomRepository extends JpaRepository<ClinicRoom, Long> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("select room from ClinicRoom room where (:specialtyId is null or room.specialtyId = :specialtyId) "
            + "and (:active is null or room.active = :active)")
    Page<ClinicRoom> search(@Param("specialtyId") Long specialtyId, @Param("active") Boolean active,
                            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from ClinicRoom room where room.id = :id")
    Optional<ClinicRoom> findByIdForUpdate(@Param("id") Long id);
}
