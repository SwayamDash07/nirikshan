package com.nirikshan.dto;

import com.nirikshan.model.RiskLevel;
import com.nirikshan.model.FlowBehaviorState;
import java.time.Instant;
import java.util.List;

public record RiskEventResponse(Long id, Long zoneId, Instant timestamp, double densityScore, int peopleCount, double movementSpeed,
                                RiskLevel riskLevel, String explanation, List<HotspotRegion> hotspotRegions, boolean bottleneckDetected, String sourceClipId,
                                double densityChange, double movementSlowdown, long hotspotPersistenceSeconds, String source,
                                String dominantDirection, Double directionDegrees, double directionConfidence,
                                double directionalConsistency, double reverseMovementRatio, double conflictingMovementRatio,
                                FlowBehaviorState behaviorState, String behaviorExplanation) {
    public RiskEventResponse(Long id, Long zoneId, Instant timestamp, double densityScore, int peopleCount, double movementSpeed,
                             RiskLevel riskLevel, String explanation, List<HotspotRegion> hotspotRegions, boolean bottleneckDetected,
                             String sourceClipId, double densityChange, double movementSlowdown, long hotspotPersistenceSeconds,
                             String source) {
        this(id, zoneId, timestamp, densityScore, peopleCount, movementSpeed, riskLevel, explanation, hotspotRegions,
                bottleneckDetected, sourceClipId, densityChange, movementSlowdown, hotspotPersistenceSeconds, source,
                null, null, 0, 0, 0, 0, FlowBehaviorState.INSUFFICIENT_DATA, "Flow data is not available for this event.");
    }
}
