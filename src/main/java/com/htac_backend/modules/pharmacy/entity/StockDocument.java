package com.htac_backend.modules.pharmacy.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "stock_document")
public class StockDocument extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "document_code", nullable = false, length = 30)
    private String documentCode;
    @Column(nullable = false, length = 20)
    private String type;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(nullable = false, length = 15)
    private String status = "DRAFT";
    @Column(name = "stock_location", nullable = false, length = 10)
    private String stockLocation;
    @Column(name = "stock_bucket", nullable = false, length = 15)
    private String stockBucket;
    @Column(name = "destination_location", length = 10)
    private String destinationLocation;
    @Column(name = "destination_bucket", length = 15)
    private String destinationBucket;
    @Column(name = "supplier_id")
    private Long supplierId;
    @Column(name = "prescription_id")
    private Long prescriptionId;
    @Column(name = "purchase_request_id")
    private Long purchaseRequestId;
    @Column(name = "posted_at")
    private Instant postedAt;
    @Column(name = "posted_by")
    private Long postedBy;
    @Column(columnDefinition = "TEXT")
    private String note;

    protected StockDocument() { }

    public StockDocument(String documentCode, String type, Instant occurredAt,
            String stockLocation, String stockBucket, String destinationLocation,
            String destinationBucket, Long supplierId, Long prescriptionId,
            Long purchaseRequestId, String note) {
        this.documentCode = documentCode;
        this.type = type;
        this.occurredAt = occurredAt;
        this.stockLocation = stockLocation;
        this.stockBucket = stockBucket;
        this.destinationLocation = destinationLocation;
        this.destinationBucket = destinationBucket;
        this.supplierId = supplierId;
        this.prescriptionId = prescriptionId;
        this.purchaseRequestId = purchaseRequestId;
        this.note = note;
    }

    public Long getId() { return id; }
    public String getDocumentCode() { return documentCode; }
    public String getType() { return type; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getStatus() { return status; }
    public String getStockLocation() { return stockLocation; }
    public String getStockBucket() { return stockBucket; }
    public String getDestinationLocation() { return destinationLocation; }
    public String getDestinationBucket() { return destinationBucket; }
    public Long getSupplierId() { return supplierId; }
    public Long getPrescriptionId() { return prescriptionId; }
    public Long getPurchaseRequestId() { return purchaseRequestId; }
    public Instant getPostedAt() { return postedAt; }
    public Long getPostedBy() { return postedBy; }
    public String getNote() { return note; }

    public void post(Long userId, Instant at) {
        this.status = "POSTED";
        this.postedBy = userId;
        this.postedAt = at;
    }

    public void cancel(String reason) {
        this.status = "CANCELLED";
        this.note = reason;
    }
}
