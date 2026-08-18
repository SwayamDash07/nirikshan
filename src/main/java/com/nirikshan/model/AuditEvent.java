package com.nirikshan.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "privacy_audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 80) private String action;
    @Column(nullable = false, length = 80) private String resourceType;
    private Long resourceId;
    private Long actorUserId;
    @Column(length = 500) private String details;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected AuditEvent() { }
    public AuditEvent(String action, String resourceType, Long resourceId, Long actorUserId, String details) {
        this.action = action; this.resourceType = resourceType; this.resourceId = resourceId;
        this.actorUserId = actorUserId; this.details = details;
    }
    @PrePersist void created() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public Long getActorUserId() { return actorUserId; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
