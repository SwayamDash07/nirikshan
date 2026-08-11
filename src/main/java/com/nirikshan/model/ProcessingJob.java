package com.nirikshan.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processing_jobs")
public class ProcessingJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "zone_id", nullable = false) private Zone zone;
    @Column(nullable = false) private String videoFilename;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ProcessingJobStatus status = ProcessingJobStatus.PENDING;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant completedAt;
    @Column(columnDefinition = "TEXT") private String errorMessage;
    private String annotatedVideoPath;
    private String summaryPath;

    @PrePersist void initializeTimestamp() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; } public Zone getZone() { return zone; } public void setZone(Zone zone) { this.zone = zone; }
    public String getVideoFilename() { return videoFilename; } public void setVideoFilename(String videoFilename) { this.videoFilename = videoFilename; }
    public ProcessingJobStatus getStatus() { return status; } public void setStatus(ProcessingJobStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; } public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getAnnotatedVideoPath() { return annotatedVideoPath; } public void setAnnotatedVideoPath(String annotatedVideoPath) { this.annotatedVideoPath = annotatedVideoPath; }
    public String getSummaryPath() { return summaryPath; } public void setSummaryPath(String summaryPath) { this.summaryPath = summaryPath; }
}
