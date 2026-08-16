package com.nirikshan.dto;

import com.nirikshan.model.RiskEventSource;
import com.nirikshan.model.RiskForecastState;
import com.nirikshan.model.RiskLevel;

import java.time.Instant;

public record CitizenRiskForecastResponse(
        Long zoneId,
        String zoneName,
        Instant generatedAt,
        Instant lastTelemetryAt,
        RiskLevel currentRisk,
        RiskLevel projectedRisk,
        RiskForecastState state,
        String message,
        boolean stale,
        RiskEventSource source) { }
