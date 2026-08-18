package com.nirikshan.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "citizen_reports")
public class CitizenReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(nullable = false)
    private Instant timestamp;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ReportStatus status = ReportStatus.OPEN;
    @Column(unique = true, length = 64)
    private String clientEventId;

    public CitizenReport() {}
    public CitizenReport(Zone zone, String description) {
        this.zone = zone; this.description = description; this.timestamp = Instant.now(); this.status = ReportStatus.OPEN;
    }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Zone getZone() { return zone; } public void setZone(Zone zone) { this.zone = zone; }
    public Long getZoneId() { return zone == null ? null : zone.getId(); }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public Instant getTimestamp() { return timestamp; } public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public ReportStatus getStatus() { return status; } public void setStatus(ReportStatus status) { this.status = status; }
    public String getClientEventId() { return clientEventId; } public void setClientEventId(String clientEventId) { this.clientEventId = clientEventId; }
}
