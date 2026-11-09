package com.htac_backend.modules.administration.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hospital_config")
public class HospitalConfig extends AuditedEntity {
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, columnDefinition = "text")
    private String address;
    @Column(nullable = false, length = 20)
    private String phone;
    @Column(length = 150)
    private String email;
    @Column(name = "logo_url", columnDefinition = "text")
    private String logoUrl;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "advance_booking_days", nullable = false)
    private int advanceBookingDays;

    protected HospitalConfig() { }

    public HospitalConfig(String name, String address, String phone, String email, String logoUrl,
            String description, int advanceBookingDays) {
        this.id = SINGLETON_ID;
        replace(name, address, phone, email, logoUrl, description, advanceBookingDays);
    }

    public void replace(String name, String address, String phone, String email, String logoUrl,
            String description, int advanceBookingDays) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.logoUrl = logoUrl;
        this.description = description;
        this.advanceBookingDays = advanceBookingDays;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getLogoUrl() { return logoUrl; }
    public String getDescription() { return description; }
    public int getAdvanceBookingDays() { return advanceBookingDays; }
}
