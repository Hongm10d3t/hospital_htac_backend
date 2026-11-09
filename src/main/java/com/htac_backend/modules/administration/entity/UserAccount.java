package com.htac_backend.modules.administration.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_account")
public class UserAccount extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected UserAccount() { }

    public UserAccount(String username, String passwordHash, String fullName, String phone, AccountRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public AccountRole getRole() { return role; }
    public boolean isActive() { return active; }

    public void update(String username, String fullName, String phone) {
        this.username = username;
        this.fullName = fullName;
        this.phone = phone;
    }

    public void changeRole(AccountRole role) { this.role = role; }
    public void setActive(boolean active) { this.active = active; }
}
