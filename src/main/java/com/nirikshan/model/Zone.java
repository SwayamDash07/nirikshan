package com.nirikshan.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity
@Table(name = "zones")
public class Zone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;
    @Column(nullable = false) private String name;
    private Double latitude;
    private Double longitude;
    private Double radiusMeters;
    @Column(columnDefinition = "TEXT") private String polygon;
    @Column(nullable = false) private double currentDensity;
    @Column(nullable = false) private int currentPeopleCount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RiskLevel currentRiskLevel = RiskLevel.LOW;
    @Column(nullable = false) private Instant lastUpdated;
    @Column(nullable = false) private boolean bottleneckDetected;

    public Zone() {}
    public Zone(Venue venue, String name, double latitude, double longitude, double radiusMeters) {
        this.venue = venue; this.name = name; this.latitude = latitude; this.longitude = longitude;
        this.radiusMeters = radiusMeters; this.lastUpdated = Instant.now();
    }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Venue getVenue() { return venue; } public void setVenue(Venue venue) { this.venue = venue; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public Double getLatitude() { return latitude; } public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; } public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getRadiusMeters() { return radiusMeters; } public void setRadiusMeters(Double radiusMeters) { this.radiusMeters = radiusMeters; }
    public String getPolygon() { return polygon; } public void setPolygon(String polygon) { this.polygon = polygon; }
    public double getCurrentDensity() { return currentDensity; } public void setCurrentDensity(double currentDensity) { this.currentDensity = currentDensity; }
    public int getCurrentPeopleCount() { return currentPeopleCount; } public void setCurrentPeopleCount(int currentPeopleCount) { this.currentPeopleCount = currentPeopleCount; }
    public RiskLevel getCurrentRiskLevel() { return currentRiskLevel; } public void setCurrentRiskLevel(RiskLevel currentRiskLevel) { this.currentRiskLevel = currentRiskLevel; }
    public Instant getLastUpdated() { return lastUpdated; } public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    public boolean isBottleneckDetected() { return bottleneckDetected; } public void setBottleneckDetected(boolean bottleneckDetected) { this.bottleneckDetected = bottleneckDetected; }
    @PrePersist @PreUpdate void touch() { if (lastUpdated == null) lastUpdated = Instant.now(); }
}
