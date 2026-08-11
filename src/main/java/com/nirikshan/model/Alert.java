package com.nirikshan.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "zone_id", nullable = false) private Zone zone;
    @Column(nullable = false) private Instant timestamp;
    @Column(nullable = false, length = 2000) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RiskLevel severity;
    @Column(nullable = false) private boolean resolved;
    private Instant resolvedAt;
    public Alert() {}
    public Alert(Zone zone, Instant timestamp, String message, RiskLevel severity) {
        this.zone = zone; this.timestamp = timestamp; this.message = message; this.severity = severity; this.resolved = false;
    }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Zone getZone() { return zone; } public void setZone(Zone zone) { this.zone = zone; }
    public Instant getTimestamp() { return timestamp; } public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getMessage() { return message; } public void setMessage(String message) { this.message = message; }
    public RiskLevel getSeverity() { return severity; } public void setSeverity(RiskLevel severity) { this.severity = severity; }
    public boolean isResolved() { return resolved; } public void setResolved(boolean resolved) { this.resolved = resolved; }
    public Instant getResolvedAt() { return resolvedAt; } public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
