package com.htac_backend.modules.clinicdirectory.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "specialty")
public class Specialty extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 20)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Specialty() { }

    public Long getId() { return id; }
    public boolean isActive() { return active; }
}
