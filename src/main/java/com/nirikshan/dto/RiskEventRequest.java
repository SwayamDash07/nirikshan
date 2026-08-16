package com.nirikshan.dto;

import com.nirikshan.model.RiskLevel;
import com.nirikshan.model.FlowBehaviorState;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public record RiskEventRequest(
        @NotNull Long zoneId,
        @NotNull Instant timestamp,
        @PositiveOrZero double densityScore,
        @PositiveOrZero int peopleCount,
        @PositiveOrZero double movementSpeed,
        @NotNull RiskLevel riskLevel,
        @NotBlank String explanation,
        List<HotspotRegion> hotspotRegions,
        String sourceClipId,
        Double densityChange,
        Double movementSlowdown,
        Double hotspotPersistenceSeconds,
        String source,
        String dominantDirection,
        Double directionDegrees,
        Double directionConfidence,
        Double directionalConsistency,
        Double reverseMovementRatio,
        Double conflictingMovementRatio,
        FlowBehaviorState behaviorState,
        String behaviorExplanation) {
    public RiskEventRequest(Long zoneId, Instant timestamp, double densityScore, int peopleCount, double movementSpeed,
                            RiskLevel riskLevel, String explanation, List<HotspotRegion> hotspotRegions,
                            String sourceClipId, Double densityChange, Double movementSlowdown,
                            Double hotspotPersistenceSeconds, String source) {
        this(zoneId, timestamp, densityScore, peopleCount, movementSpeed, riskLevel, explanation, hotspotRegions,
                sourceClipId, densityChange, movementSlowdown, hotspotPersistenceSeconds, source,
                null, null, null, null, null, null, null, null);
    }
}
