package com.nirikshan.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "venues")
public class Venue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(length = 2000)
    private String description;
    private Double latitude;
    private Double longitude;
    @Column(name = "service_radius_meters", nullable = false)
    private Double serviceRadiusMeters = 1000.0;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @JsonIgnore
    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Zone> zones = new ArrayList<>();

    public Venue() {}
    public Venue(String name, String description) { this.name = name; this.description = description; this.createdAt = Instant.now(); }
    public Venue(String name, String description, double latitude, double longitude) { this(name, description); this.latitude = latitude; this.longitude = longitude; }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public Double getLatitude() { return latitude; } public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; } public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getServiceRadiusMeters() { return serviceRadiusMeters; } public void setServiceRadiusMeters(Double serviceRadiusMeters) { this.serviceRadiusMeters = serviceRadiusMeters; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<Zone> getZones() { return zones; } public void setZones(List<Zone> zones) { this.zones = zones; }
    @PrePersist void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
