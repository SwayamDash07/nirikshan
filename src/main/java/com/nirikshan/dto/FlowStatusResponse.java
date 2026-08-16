package com.nirikshan.dto;

import com.nirikshan.model.FlowBehaviorState;
import java.time.Instant;

public record FlowStatusResponse(
        Long zoneId,
        String zoneName,
        Instant observedAt,
        String dominantDirection,
        Double directionDegrees,
        double directionConfidence,
        double directionalConsistency,
        double reverseMovementRatio,
        double conflictingMovementRatio,
        FlowBehaviorState behaviorState,
        String behaviorExplanation,
        boolean sufficientData,
        Instant analysisGeneratedAt,
        Instant analysisWindowStart,
        Instant analysisWindowEnd,
        Instant nextAnalysisAt,
        long analysisIntervalSeconds,
        String dataSufficiency,
        UnusualBehaviorResponse unusualBehavior) {
    public FlowStatusResponse(Long zoneId, String zoneName, Instant observedAt, String dominantDirection,
                               Double directionDegrees, double directionConfidence, double directionalConsistency,
                               double reverseMovementRatio, double conflictingMovementRatio, FlowBehaviorState behaviorState,
                               String behaviorExplanation, boolean sufficientData, Instant analysisGeneratedAt,
                               Instant analysisWindowStart, Instant analysisWindowEnd, Instant nextAnalysisAt,
                               long analysisIntervalSeconds, String dataSufficiency) {
        this(zoneId, zoneName, observedAt, dominantDirection, directionDegrees, directionConfidence, directionalConsistency,
                reverseMovementRatio, conflictingMovementRatio, behaviorState, behaviorExplanation, sufficientData,
                analysisGeneratedAt, analysisWindowStart, analysisWindowEnd, nextAnalysisAt, analysisIntervalSeconds,
                dataSufficiency, UnusualBehaviorResponse.insufficient("Unusual behavior requires persistent readings."));
    }
}
