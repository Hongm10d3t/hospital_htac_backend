package com.htac_backend.modules.administration.repository;

import com.htac_backend.modules.administration.entity.HospitalConfig;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface HospitalConfigRepository extends JpaRepository<HospitalConfig, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select config from HospitalConfig config where config.id = 1")
    Optional<HospitalConfig> findSingletonForUpdate();
}
