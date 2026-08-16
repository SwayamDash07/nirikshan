package com.nirikshan.dto;

import com.nirikshan.model.RiskEventSource;
import com.nirikshan.model.RiskForecastState;
import com.nirikshan.model.RiskLevel;

import java.time.Instant;
import java.util.List;

public record RiskForecastResponse(
        Long zoneId,
        String zoneName,
        Instant generatedAt,
        Instant lastTelemetryAt,
        RiskLevel currentRisk,
        RiskLevel projectedRisk,
        int forecastHorizonSeconds,
        Long estimatedSecondsToProjectedRisk,
        double currentDensity,
        double projectedDensity,
        double densityTrendPerMinute,
        double currentMovementSpeed,
        double movementSlowdown,
        double movementSlowdownTrendPerMinute,
        long hotspotPersistenceSeconds,
        boolean bottleneckDetected,
        double confidence,
        RiskForecastState state,
        String explanation,
        RiskEventSource source,
        boolean stale,
        List<RiskForecastPoint> projections) { }
