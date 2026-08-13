package com.nirikshan.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="security_instructions")
public class SecurityInstruction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=2000) private String message;
 @ManyToOne(fetch=FetchType.LAZY) private Zone targetZone;
 @ManyToOne(fetch=FetchType.LAZY) private User targetSecurityUser;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) private User createdBy;
 @Column(nullable=false,updatable=false) private Instant createdAt;
 @PrePersist void created(){if(createdAt==null)createdAt=Instant.now();}
 public Long getId(){return id;} public String getMessage(){return message;} public void setMessage(String v){message=v;} public Zone getTargetZone(){return targetZone;} public void setTargetZone(Zone v){targetZone=v;} public User getTargetSecurityUser(){return targetSecurityUser;} public void setTargetSecurityUser(User v){targetSecurityUser=v;} public User getCreatedBy(){return createdBy;} public void setCreatedBy(User v){createdBy=v;} public Instant getCreatedAt(){return createdAt;}
}
