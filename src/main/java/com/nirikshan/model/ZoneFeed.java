package com.nirikshan.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "zone_feeds")
public class ZoneFeed {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false, unique = true)
    private Zone zone;

    @Column(nullable = false, length = 1000)
    private String videoPath;

    @Column(nullable = false)
    private String videoFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ZoneFeedStatus status = ZoneFeedStatus.OFFLINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrivacyStatus privacyStatus = PrivacyStatus.PENDING;

    private Instant startedAt;

    @Column(nullable = false)
    private int currentLoopIteration;

    public Long getId() { return id; }
    public Zone getZone() { return zone; }
    public void setZone(Zone zone) { this.zone = zone; }
    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }
    public String getVideoFilename() { return videoFilename; }
    public void setVideoFilename(String videoFilename) { this.videoFilename = videoFilename; }
    public ZoneFeedStatus getStatus() { return status; }
    public void setStatus(ZoneFeedStatus status) { this.status = status; }
    public PrivacyStatus getPrivacyStatus() { return privacyStatus; }
    public void setPrivacyStatus(PrivacyStatus privacyStatus) { this.privacyStatus = privacyStatus; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public int getCurrentLoopIteration() { return currentLoopIteration; }
    public void setCurrentLoopIteration(int currentLoopIteration) { this.currentLoopIteration = currentLoopIteration; }
}
