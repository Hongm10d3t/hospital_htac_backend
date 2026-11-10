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
import java.math.BigDecimal;

@Entity
@Table(name = "stock_document_item")
public class StockDocumentItem extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private StockDocument document;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private MedicineBatch batch;
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;
    @Column(name = "unit_cost", precision = 14, scale = 2)
    private BigDecimal unitCost;
    @Column(name = "prescription_item_id")
    private Long prescriptionItemId;
    @Column(name = "source_item_id")
    private Long sourceItemId;
    @Column(name = "book_quantity", precision = 14, scale = 3)
    private BigDecimal bookQuantity;
    @Column(name = "counted_quantity", precision = 14, scale = 3)
    private BigDecimal countedQuantity;
    @Column(columnDefinition = "TEXT")
    private String note;

    protected StockDocumentItem() { }

    public StockDocumentItem(StockDocument document, MedicineBatch batch, BigDecimal quantity,
            BigDecimal unitCost, Long prescriptionItemId, Long sourceItemId,
            BigDecimal bookQuantity, BigDecimal countedQuantity, String note) {
        this.document = document;
        this.batch = batch;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.prescriptionItemId = prescriptionItemId;
        this.sourceItemId = sourceItemId;
        this.bookQuantity = bookQuantity;
        this.countedQuantity = countedQuantity;
        this.note = note;
    }

    public Long getId() { return id; }
    public StockDocument getDocument() { return document; }
    public MedicineBatch getBatch() { return batch; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public Long getPrescriptionItemId() { return prescriptionItemId; }
    public Long getSourceItemId() { return sourceItemId; }
    public BigDecimal getBookQuantity() { return bookQuantity; }
    public BigDecimal getCountedQuantity() { return countedQuantity; }
    public String getNote() { return note; }
}
