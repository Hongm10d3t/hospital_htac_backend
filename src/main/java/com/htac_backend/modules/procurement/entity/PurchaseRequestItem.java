package com.htac_backend.modules.procurement.entity;

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
import java.math.BigDecimal;

@Entity
@Table(name = "purchase_request_item")
public class PurchaseRequestItem extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private PurchaseRequest request;
    @Column(name = "medicine_id", nullable = false)
    private Long medicineId;
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;
    @Column(columnDefinition = "TEXT")
    private String note;

    protected PurchaseRequestItem() { }

    public PurchaseRequestItem(PurchaseRequest request, Long medicineId, BigDecimal quantity, String note) {
        this.request = request;
        this.medicineId = medicineId;
        this.quantity = quantity;
        this.note = note;
    }

    public Long getId() { return id; }
    public PurchaseRequest getRequest() { return request; }
    public Long getMedicineId() { return medicineId; }
    public BigDecimal getQuantity() { return quantity; }
    public String getNote() { return note; }
}
