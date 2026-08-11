package com.nirikshan.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "risk_events")
public class RiskEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "zone_id", nullable = false) private Zone zone;
    @Column(nullable = false) private Instant timestamp;
    @Column(nullable = false) private double densityScore;
    @Column(nullable = false) private int peopleCount;
    @Column(nullable = false) private double movementSpeed;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RiskLevel riskLevel;
    @Column(nullable = false, columnDefinition = "TEXT") private String explanation;
    private String sourceClipId;
    public RiskEvent() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Zone getZone() { return zone; } public void setZone(Zone zone) { this.zone = zone; }
    public Instant getTimestamp() { return timestamp; } public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public double getDensityScore() { return densityScore; } public void setDensityScore(double densityScore) { this.densityScore = densityScore; }
    public int getPeopleCount() { return peopleCount; } public void setPeopleCount(int peopleCount) { this.peopleCount = peopleCount; }
    public double getMovementSpeed() { return movementSpeed; } public void setMovementSpeed(double movementSpeed) { this.movementSpeed = movementSpeed; }
    public RiskLevel getRiskLevel() { return riskLevel; } public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public String getExplanation() { return explanation; } public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getSourceClipId() { return sourceClipId; } public void setSourceClipId(String sourceClipId) { this.sourceClipId = sourceClipId; }
}
