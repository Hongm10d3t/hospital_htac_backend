package com.htac_backend.modules.administration.repository;

import com.htac_backend.modules.administration.entity.PasswordResetAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetAuditRepository extends JpaRepository<PasswordResetAudit, Long> {
}
