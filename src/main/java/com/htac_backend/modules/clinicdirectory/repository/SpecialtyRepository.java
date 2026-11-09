package com.htac_backend.modules.clinicdirectory.repository;

import com.htac_backend.modules.clinicdirectory.entity.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {
}
