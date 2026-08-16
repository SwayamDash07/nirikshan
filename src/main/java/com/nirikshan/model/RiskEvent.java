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
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RiskEventSource source = RiskEventSource.LIVE;
    @Column(nullable = false, columnDefinition = "TEXT") private String explanation;
    @Column(columnDefinition = "TEXT") private String hotspotRegions;
    @Column(nullable = false) private double densityChange;
    @Column(nullable = false) private double movementSlowdown;
    @Column(nullable = false) private long hotspotPersistenceSeconds;
    private String dominantDirection;
    private Double directionDegrees;
    @Column(nullable = false) private double directionConfidence;
    @Column(nullable = false) private double directionalConsistency;
    @Column(nullable = false) private double reverseMovementRatio;
    @Column(nullable = false) private double conflictingMovementRatio;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FlowBehaviorState behaviorState = FlowBehaviorState.INSUFFICIENT_DATA;
    @Column(columnDefinition = "TEXT") private String behaviorExplanation;
    private String sourceClipId;
    public RiskEvent() {}
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Zone getZone() { return zone; } public void setZone(Zone zone) { this.zone = zone; }
    public Instant getTimestamp() { return timestamp; } public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public double getDensityScore() { return densityScore; } public void setDensityScore(double densityScore) { this.densityScore = densityScore; }
    public int getPeopleCount() { return peopleCount; } public void setPeopleCount(int peopleCount) { this.peopleCount = peopleCount; }
    public double getMovementSpeed() { return movementSpeed; } public void setMovementSpeed(double movementSpeed) { this.movementSpeed = movementSpeed; }
    public RiskLevel getRiskLevel() { return riskLevel; } public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public RiskEventSource getSource() { return source; } public void setSource(RiskEventSource source) { this.source = source == null ? RiskEventSource.LIVE : source; }
    public String getExplanation() { return explanation; } public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getHotspotRegions() { return hotspotRegions; } public void setHotspotRegions(String hotspotRegions) { this.hotspotRegions = hotspotRegions; }
    public double getDensityChange() { return densityChange; } public void setDensityChange(double value) { densityChange = value; }
    public double getMovementSlowdown() { return movementSlowdown; } public void setMovementSlowdown(double value) { movementSlowdown = value; }
    public long getHotspotPersistenceSeconds() { return hotspotPersistenceSeconds; } public void setHotspotPersistenceSeconds(long value) { hotspotPersistenceSeconds = value; }
    public String getSourceClipId() { return sourceClipId; } public void setSourceClipId(String sourceClipId) { this.sourceClipId = sourceClipId; }
    public String getDominantDirection() { return dominantDirection; } public void setDominantDirection(String value) { dominantDirection = value; }
    public Double getDirectionDegrees() { return directionDegrees; } public void setDirectionDegrees(Double value) { directionDegrees = value; }
    public double getDirectionConfidence() { return directionConfidence; } public void setDirectionConfidence(double value) { directionConfidence = value; }
    public double getDirectionalConsistency() { return directionalConsistency; } public void setDirectionalConsistency(double value) { directionalConsistency = value; }
    public double getReverseMovementRatio() { return reverseMovementRatio; } public void setReverseMovementRatio(double value) { reverseMovementRatio = value; }
    public double getConflictingMovementRatio() { return conflictingMovementRatio; } public void setConflictingMovementRatio(double value) { conflictingMovementRatio = value; }
    public FlowBehaviorState getBehaviorState() { return behaviorState; } public void setBehaviorState(FlowBehaviorState value) { behaviorState = value == null ? FlowBehaviorState.INSUFFICIENT_DATA : value; }
    public String getBehaviorExplanation() { return behaviorExplanation; } public void setBehaviorExplanation(String value) { behaviorExplanation = value; }
}
