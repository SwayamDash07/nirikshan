package com.nirikshan.dto;

import com.nirikshan.model.RiskLevel;
import com.nirikshan.model.ZoneFeedStatus;
import com.nirikshan.model.PrivacyStatus;

import java.time.Instant;

public record AdminZoneResponse(
        Long id,
        String name,
        Double latitude,
        Double longitude,
        Double radiusMeters,
        double currentDensity,
        int currentPeopleCount,
        RiskLevel currentRiskLevel,
        Instant lastUpdated,
        ZoneFeedStatus feedStatus,
        PrivacyStatus privacyStatus,
        String videoFilename,
        String videoUrl,
        Instant feedStartedAt,
        int currentLoopIteration,
        boolean bottleneckDetected
) {}
