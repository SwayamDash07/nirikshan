package com.nirikshan.dto;

import com.nirikshan.model.ProcessingJobStatus;
import java.time.Instant;

public record ProcessingJobResponse(Long id, Long zoneId, String zoneName, String videoFilename,
                                    ProcessingJobStatus status, Instant createdAt, Instant completedAt,
                                    String errorMessage, String annotatedVideoPath, String summaryPath) {}
