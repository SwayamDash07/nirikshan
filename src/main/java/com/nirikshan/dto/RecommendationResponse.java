package com.nirikshan.dto;

import com.nirikshan.model.RecommendationStatus;
import com.nirikshan.model.RecommendationType;
import com.nirikshan.model.RiskLevel;
import java.time.Instant;

public record RecommendationResponse(
        Long id, Long zoneId, String zoneName, RecommendationType type, String message,
        RiskLevel severity, Instant createdAt, RecommendationStatus status, Long acknowledgedByUserId) { }
