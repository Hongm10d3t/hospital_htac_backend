package com.htac_backend.modules.clinicdirectory.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "clinic_room")
public class ClinicRoom extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "specialty_id", nullable = false)
    private Long specialtyId;
    @Column(nullable = false, length = 20)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 200)
    private String location;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected ClinicRoom() { }

    public ClinicRoom(Long specialtyId, String code, String name, String location) {
        this.specialtyId = specialtyId;
        this.code = code;
        this.name = name;
        this.location = location;
    }

    public Long getId() { return id; }
    public Long getSpecialtyId() { return specialtyId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public boolean isActive() { return active; }

    public void update(Long specialtyId, String code, String name, String location) {
        this.specialtyId = specialtyId;
        this.code = code;
        this.name = name;
        this.location = location;
    }

    public void setActive(boolean active) { this.active = active; }
}
