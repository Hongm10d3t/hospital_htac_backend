package com.htac_backend.modules.outpatient.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "prescription_item")
public class PrescriptionItem extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;
    @Column(name = "medicine_id", nullable = false)
    private Long medicineId;
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;
    @Column(name = "usage_instruction", nullable = false, columnDefinition = "text")
    private String usageInstruction;
    @Column(name = "unfilled_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal unfilledQuantity = BigDecimal.ZERO;
    @Column(name = "unfilled_reason", length = 20)
    private String unfilledReason;
    @Column(name = "unfilled_note", columnDefinition = "text")
    private String unfilledNote;

    protected PrescriptionItem() { }

    public PrescriptionItem(Long prescriptionId, Long medicineId, BigDecimal quantity, String instruction) {
        this.prescriptionId = prescriptionId;
        this.medicineId = medicineId;
        this.quantity = quantity;
        this.usageInstruction = instruction;
    }

    public Long getId() { return id; }
    public Long getPrescriptionId() { return prescriptionId; }
    public Long getMedicineId() { return medicineId; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUsageInstruction() { return usageInstruction; }
    public BigDecimal getUnfilledQuantity() { return unfilledQuantity; }
    public String getUnfilledReason() { return unfilledReason; }
    public String getUnfilledNote() { return unfilledNote; }

    public void recordUnfilled(BigDecimal quantity, String reason, String note) {
        this.unfilledQuantity = quantity;
        this.unfilledReason = reason;
        this.unfilledNote = note;
    }
}
