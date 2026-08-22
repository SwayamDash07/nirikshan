package com.nirikshan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "check_ins")
public class CheckIn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "staff_name", nullable = false, columnDefinition = "text")
    private String staffName;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String status = "pending";

    @PrePersist
    void created() {
        if (triggeredAt == null) triggeredAt = Instant.now();
        if (status == null || status.isBlank()) status = "pending";
    }

    public Integer getId() { return id; }
    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
