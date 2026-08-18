package com.nirikshan.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "announcement_drafts")
public class AnnouncementDraft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "zone_id") private Zone targetZone;
    @Column(nullable = false, length = 2000) private String englishText;
    @Column(nullable = false, length = 2000) private String hindiText;
    @Column(nullable = false, length = 2000) private String odiaText;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RiskLevel urgency;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RiskEventSource source = RiskEventSource.LIVE;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AnnouncementApprovalStatus approvalStatus = AnnouncementApprovalStatus.PENDING_APPROVAL;
    @Column(nullable = false) private boolean sent;
    private Instant sentAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "approved_by_user_id") private User approvedByUser;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    @PrePersist void created() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public Zone getTargetZone() { return targetZone; } public void setTargetZone(Zone value) { targetZone = value; }
    public String getEnglishText() { return englishText; } public void setEnglishText(String value) { englishText = value; }
    public String getHindiText() { return hindiText; } public void setHindiText(String value) { hindiText = value; }
    public String getOdiaText() { return odiaText; } public void setOdiaText(String value) { odiaText = value; }
    public RiskLevel getUrgency() { return urgency; } public void setUrgency(RiskLevel value) { urgency = value; }
    public RiskEventSource getSource() { return source; } public void setSource(RiskEventSource value) { source = value == null ? RiskEventSource.LIVE : value; }
    public AnnouncementApprovalStatus getApprovalStatus() { return approvalStatus; } public void setApprovalStatus(AnnouncementApprovalStatus value) { approvalStatus = value; }
    public boolean isSent() { return sent; } public void setSent(boolean value) { sent = value; }
    public Instant getSentAt() { return sentAt; } public void setSentAt(Instant value) { sentAt = value; }
    public User getApprovedByUser() { return approvedByUser; } public void setApprovedByUser(User value) { approvedByUser = value; }
    public Instant getCreatedAt() { return createdAt; }
}
