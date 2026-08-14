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
    @Column(nullable = false) private Instant createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RecommendationStatus status = RecommendationStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "acknowledged_by_user_id") private User acknowledgedByUser;

    public Recommendation() { }
    public Recommendation(Zone zone, RecommendationType type, String message, RiskLevel severity) {
        this.zone = zone; this.type = type; this.message = message; this.severity = severity; this.createdAt = Instant.now();
    }
    public Long getId() { return id; } public Zone getZone() { return zone; } public RecommendationType getType() { return type; }
    public String getMessage() { return message; } public RiskLevel getSeverity() { return severity; } public Instant getCreatedAt() { return createdAt; }
    public RecommendationStatus getStatus() { return status; } public void setStatus(RecommendationStatus status) { this.status = status; }
    public User getAcknowledgedByUser() { return acknowledgedByUser; } public void setAcknowledgedByUser(User user) { this.acknowledgedByUser = user; }
}
