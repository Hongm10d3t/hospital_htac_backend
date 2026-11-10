package com.htac_backend.modules.pharmacy.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "medicine_batch")
public class MedicineBatch extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;
    @Column(name = "batch_number", nullable = false, length = 60)
    private String batchNumber;
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;
    @Column(nullable = false, length = 15)
    private String status = "ACTIVE";
    @Column(name = "block_reason", columnDefinition = "TEXT")
    private String blockReason;

    protected MedicineBatch() { }

    public MedicineBatch(Medicine medicine, String batchNumber, LocalDate expiryDate) {
        this.medicine = medicine;
        this.batchNumber = batchNumber;
        this.expiryDate = expiryDate;
    }

    public Long getId() { return id; }
    public Medicine getMedicine() { return medicine; }
    public String getBatchNumber() { return batchNumber; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public String getStatus() { return status; }
    public String getBlockReason() { return blockReason; }

    public void update(String batchNumber, LocalDate expiryDate, String status, String blockReason) {
        this.batchNumber = batchNumber;
        this.expiryDate = expiryDate;
        this.status = status;
        this.blockReason = blockReason;
    }
}
