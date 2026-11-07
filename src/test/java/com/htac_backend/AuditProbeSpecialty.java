package com.htac_backend;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Test-only mapping to verify the shared audit listener until M02 owns this entity. */
@Entity
@Table(name = "specialty")
class AuditProbeSpecialty extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    protected AuditProbeSpecialty() { }

    AuditProbeSpecialty(String code, String name) {
        this.code = code;
        this.name = name;
    }

    Long getId() { return id; }
    void rename(String newName) { this.name = newName; }
}
