package com.nirikshan.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "recommendations")
public class Recommendation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "zone_id") private Zone zone;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RecommendationType type;
    @Column(nullable = false, length = 2000) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RiskLevel severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RiskEventSource source = RiskEventSource.LIVE;
    @Column(nullable = false) private Instant createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RecommendationStatus status = RecommendationStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "acknowledged_by_user_id") private User acknowledgedByUser;
    @Column(length = 255) private String affectedRoute;
    @Column(length = 64) private String flowDirection;
    private Integer durationMinutes;
    private Double confidence;
    @Column(length = 64) private String barricadeInstruction;

    public Recommendation() { }
    public Recommendation(Zone zone, RecommendationType type, String message, RiskLevel severity) {
        this.zone = zone; this.type = type; this.message = message; this.severity = severity; this.createdAt = Instant.now();
    }
    public Recommendation(Zone zone, RecommendationType type, String message, RiskLevel severity, RiskEventSource source) {
        this(zone, type, message, severity); this.source = source == null ? RiskEventSource.LIVE : source;
    }
    public Long getId() { return id; } public Zone getZone() { return zone; } public RecommendationType getType() { return type; }
    public String getMessage() { return message; } public void setMessage(String message) { this.message = message; }
    public RiskLevel getSeverity() { return severity; } public void setSeverity(RiskLevel severity) { this.severity = severity; }
    public Instant getCreatedAt() { return createdAt; }
    public RecommendationStatus getStatus() { return status; } public void setStatus(RecommendationStatus status) { this.status = status; }
    public RiskEventSource getSource() { return source; } public void setSource(RiskEventSource source) { this.source = source == null ? RiskEventSource.LIVE : source; }
    public User getAcknowledgedByUser() { return acknowledgedByUser; } public void setAcknowledgedByUser(User user) { this.acknowledgedByUser = user; }
    public String getAffectedRoute() { return affectedRoute; } public void setAffectedRoute(String value) { affectedRoute = value; }
    public String getFlowDirection() { return flowDirection; } public void setFlowDirection(String value) { flowDirection = value; }
    public Integer getDurationMinutes() { return durationMinutes; } public void setDurationMinutes(Integer value) { durationMinutes = value; }
    public Double getConfidence() { return confidence; } public void setConfidence(Double value) { confidence = value; }
    public String getBarricadeInstruction() { return barricadeInstruction; } public void setBarricadeInstruction(String value) { barricadeInstruction = value; }
}
