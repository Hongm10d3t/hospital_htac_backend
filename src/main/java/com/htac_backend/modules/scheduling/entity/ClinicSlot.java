package com.htac_backend.modules.scheduling.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "clinic_slot")
public class ClinicSlot extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "room_id", nullable = false)
    private Long roomId;
    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    @Column(name = "end_at", nullable = false)
    private Instant endAt;
    @Column(nullable = false)
    private int capacity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ClinicSlotStatus status = ClinicSlotStatus.OPEN;

    protected ClinicSlot() { }

    public ClinicSlot(Long roomId, Long doctorId, Instant startAt, Instant endAt, int capacity) {
        this.roomId = roomId;
        this.doctorId = doctorId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.capacity = capacity;
    }

    public Long getId() { return id; }
    public Long getRoomId() { return roomId; }
    public Long getDoctorId() { return doctorId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public int getCapacity() { return capacity; }
    public ClinicSlotStatus getStatus() { return status; }

    public void replace(Long roomId, Long doctorId, Instant startAt, Instant endAt, int capacity) {
        this.roomId = roomId;
        this.doctorId = doctorId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.capacity = capacity;
    }

    public void close() { this.status = ClinicSlotStatus.CLOSED; }
}
