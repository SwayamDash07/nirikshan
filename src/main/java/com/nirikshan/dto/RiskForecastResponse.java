package com.nirikshan.dto;

import com.nirikshan.model.RiskEventSource;
import com.nirikshan.model.RiskForecastState;
import com.nirikshan.model.RiskLevel;
import com.nirikshan.model.FlowBehaviorState;

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
        List<RiskForecastPoint> projections,
        String dominantDirection,
        Double directionDegrees,
        double directionConfidence,
        double directionalConsistency,
        double reverseMovementRatio,
        double conflictingMovementRatio,
        FlowBehaviorState behaviorState,
        String behaviorExplanation,
        Instant analysisGeneratedAt,
        Instant analysisWindowStart,
        Instant analysisWindowEnd,
        Instant nextAnalysisAt,
        long analysisIntervalSeconds,
        String dataSufficiency,
        FlowBehaviorState flowState,
        String direction,
        int analysisPeopleCount,
        List<HotspotRegion> analysisHotspotRegions,
        StampedeLikelihoodResponse stampedeLikelihood,
        PanicPropagationResponse panicPropagation,
        UnusualBehaviorResponse unusualBehavior) {
    public RiskForecastResponse(Long zoneId, String zoneName, Instant generatedAt, Instant lastTelemetryAt,
                                RiskLevel currentRisk, RiskLevel projectedRisk, int forecastHorizonSeconds,
                                Long estimatedSecondsToProjectedRisk, double currentDensity, double projectedDensity,
                                double densityTrendPerMinute, double currentMovementSpeed, double movementSlowdown,
                                double movementSlowdownTrendPerMinute, long hotspotPersistenceSeconds,
                                boolean bottleneckDetected, double confidence, RiskForecastState state, String explanation,
                                RiskEventSource source, boolean stale, List<RiskForecastPoint> projections,
                                String dominantDirection, Double directionDegrees, double directionConfidence,
                                double directionalConsistency, double reverseMovementRatio,
                                double conflictingMovementRatio, FlowBehaviorState behaviorState, String behaviorExplanation,
                                Instant analysisGeneratedAt, Instant analysisWindowStart, Instant analysisWindowEnd,
                                Instant nextAnalysisAt, long analysisIntervalSeconds, String dataSufficiency,
                                FlowBehaviorState flowState, String direction, int analysisPeopleCount,
                                List<HotspotRegion> analysisHotspotRegions) {
        this(zoneId, zoneName, generatedAt, lastTelemetryAt, currentRisk, projectedRisk, forecastHorizonSeconds,
                estimatedSecondsToProjectedRisk, currentDensity, projectedDensity, densityTrendPerMinute,
                currentMovementSpeed, movementSlowdown, movementSlowdownTrendPerMinute, hotspotPersistenceSeconds,
                bottleneckDetected, confidence, state, explanation, source, stale, projections, dominantDirection,
                directionDegrees, directionConfidence, directionalConsistency, reverseMovementRatio,
                conflictingMovementRatio, behaviorState, behaviorExplanation, analysisGeneratedAt, analysisWindowStart,
                analysisWindowEnd, nextAnalysisAt, analysisIntervalSeconds, dataSufficiency, flowState, direction,
                analysisPeopleCount, analysisHotspotRegions, StampedeLikelihoodResponse.insufficient("Stampede likelihood is unavailable until the minimum telemetry window is met."),
                PanicPropagationResponse.insufficient(source), UnusualBehaviorResponse.insufficient("Unusual behavior requires persistent readings."));
    }
}
