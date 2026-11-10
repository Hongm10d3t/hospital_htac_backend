package com.htac_backend.modules.pharmacy.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "supplier")
public class Supplier extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 20)
    private String phone;
    @Column(columnDefinition = "text")
    private String address;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Supplier() { }

    public Supplier(String code, String name, String phone, String address) {
        this.code = code;
        this.name = name;
        this.phone = phone;
        this.address = address;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public boolean isActive() { return active; }

    public void update(String code, String name, String phone, String address, boolean active) {
        this.code = code;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.active = active;
    }
}
