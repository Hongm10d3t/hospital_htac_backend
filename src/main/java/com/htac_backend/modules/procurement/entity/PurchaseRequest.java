package com.htac_backend.modules.procurement.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "purchase_request")
public class PurchaseRequest extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_code", nullable = false, length = 30)
    private String requestCode;
    @Column(name = "supplier_id")
    private Long supplierId;
    @Column(nullable = false, length = 15)
    private String status = "DRAFT";
    @Column(name = "reviewed_by")
    private Long reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;
    @Column(name = "ordered_at")
    private Instant orderedAt;
    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "close_note", columnDefinition = "TEXT")
    private String closeNote;
    @Column(columnDefinition = "TEXT")
    private String note;

    protected PurchaseRequest() { }

    public PurchaseRequest(String requestCode, Long supplierId, String note) {
        this.requestCode = requestCode;
        this.supplierId = supplierId;
        this.note = note;
    }

    public Long getId() { return id; }
    public String getRequestCode() { return requestCode; }
    public Long getSupplierId() { return supplierId; }
    public String getStatus() { return status; }
    public Long getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public Instant getOrderedAt() { return orderedAt; }
    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public Instant getClosedAt() { return closedAt; }
    public String getCloseNote() { return closeNote; }
    public String getNote() { return note; }

    public void revise(Long supplierId, String note) { this.supplierId = supplierId; this.note = note; }
    public void submit() { status = "PENDING"; }
    public void cancel() { status = "CANCELLED"; }
    public void review(String decision, Long reviewer, Instant at, String reason) {
        status = decision;
        reviewedBy = reviewer;
        reviewedAt = at;
        reviewNote = reason;
    }
    public void order(Instant at, LocalDate expectedDate) {
        orderedAt = at;
        expectedDeliveryDate = expectedDate;
    }
    public void close(Instant at, String reason) { closedAt = at; closeNote = reason; }
}
