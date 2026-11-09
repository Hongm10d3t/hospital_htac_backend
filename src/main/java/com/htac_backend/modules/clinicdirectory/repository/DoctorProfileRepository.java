package com.htac_backend.modules.clinicdirectory.repository;

import com.htac_backend.modules.clinicdirectory.entity.DoctorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, Long> {
}
