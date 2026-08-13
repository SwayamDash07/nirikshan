package com.nirikshan.model;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "users")
public class User {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(nullable = false, unique = true) private String email;
 @Column(nullable = false) private String passwordHash;
 @Enumerated(EnumType.STRING) @Column(nullable = false) private UserRole role;
 @Column(nullable = false) private String name;
 @Column(nullable = false) private boolean mustChangePassword;
 @Column(nullable = false) private boolean protectedAdmin;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_zone_id") private Zone assignedZone;
 @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_admin_id") private User createdByAdmin;
 @Column(nullable = false, updatable = false) private Instant createdAt;
 @Column(nullable = false) private boolean active = true;
 @PrePersist void created() { if (createdAt == null) createdAt = Instant.now(); }
 public Long getId(){return id;} public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;} public UserRole getRole(){return role;} public void setRole(UserRole v){role=v;} public String getName(){return name;} public void setName(String v){name=v;} public boolean isMustChangePassword(){return mustChangePassword;} public void setMustChangePassword(boolean v){mustChangePassword=v;} public boolean isProtectedAdmin(){return protectedAdmin;} public void setProtectedAdmin(boolean v){protectedAdmin=v;} public Zone getAssignedZone(){return assignedZone;} public void setAssignedZone(Zone v){assignedZone=v;} public User getCreatedByAdmin(){return createdByAdmin;} public void setCreatedByAdmin(User v){createdByAdmin=v;} public Instant getCreatedAt(){return createdAt;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
