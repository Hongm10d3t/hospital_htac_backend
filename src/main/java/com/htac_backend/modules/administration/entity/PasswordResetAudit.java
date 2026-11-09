package com.htac_backend.modules.administration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "account_password_reset_audit")
public class PasswordResetAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "target_user_id", nullable = false, updatable = false)
    private Long targetUserId;
    @Column(name = "admin_user_id", nullable = false, updatable = false)
    private Long adminUserId;
    @Enumerated(EnumType.STRING)
    @Column(name = "verified_via", nullable = false, length = 20, updatable = false)
    private PasswordResetVerificationMethod verifiedVia;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetAudit() { }

    public PasswordResetAudit(Long targetUserId, Long adminUserId,
            PasswordResetVerificationMethod verifiedVia) {
        this.targetUserId = targetUserId;
        this.adminUserId = adminUserId;
        this.verifiedVia = verifiedVia;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getTargetUserId() { return targetUserId; }
    public Long getAdminUserId() { return adminUserId; }
    public PasswordResetVerificationMethod getVerifiedVia() { return verifiedVia; }
    public Instant getCreatedAt() { return createdAt; }
}
