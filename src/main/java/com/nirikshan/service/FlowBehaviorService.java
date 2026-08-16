package com.nirikshan.service;

import com.nirikshan.dto.RiskEventRequest;
import com.nirikshan.model.FlowBehaviorState;
import com.nirikshan.model.RiskEvent;
import com.nirikshan.repository.RiskEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic flow interpretation with explicit data sufficiency and hysteresis. */
@Service
public class FlowBehaviorService {
    public static final int MIN_TRACKED_PEOPLE = 3;
    public static final double MIN_DIRECTION_CONFIDENCE = .35;
    public static final double REVERSE_THRESHOLD = .45;
    public static final double CONFLICTING_THRESHOLD = .30;
    public static final long MIN_STATE_DURATION_SECONDS = 10;

    private final RiskEventRepository events;

    public FlowBehaviorService(RiskEventRepository events) { this.events = events; }

    public Analysis analyze(Long zoneId, RiskEventRequest request, RiskEvent previous) {
        double confidence = finiteOrZero(request.directionConfidence());
        double reverse = clamp(request.reverseMovementRatio());
        double conflicting = clamp(request.conflictingMovementRatio());
        FlowBehaviorState candidate = candidate(request, confidence, reverse, conflicting, previous);
        List<RiskEvent> history = new ArrayList<>(events.findByZoneIdOrderByTimestampDesc(zoneId, PageRequest.of(0, 10)));
        history.sort(Comparator.comparing(RiskEvent::getTimestamp));
        FlowBehaviorState stable = resolveWithHysteresis(history, request.timestamp(), candidate);
        String explanation = explanation(stable, candidate, confidence, reverse, conflicting, request);
        return new Analysis(stable, explanation, confidence, reverse, conflicting);
    }

    public static FlowBehaviorState candidate(RiskEventRequest request, double confidence, double reverse,
                                              double conflicting, RiskEvent previous) {
        if (request.behaviorState() == FlowBehaviorState.INSUFFICIENT_DATA
                || confidence < MIN_DIRECTION_CONFIDENCE
                || request.dominantDirection() == null
                || request.dominantDirection().isBlank()) return FlowBehaviorState.INSUFFICIENT_DATA;
        if (request.behaviorState() != null) return request.behaviorState();
        if (reverse >= REVERSE_THRESHOLD) return FlowBehaviorState.REVERSE_FLOW;
        if (conflicting >= CONFLICTING_THRESHOLD) return FlowBehaviorState.CONFLICTING_FLOW;
        if (previous != null && request.movementSpeed() < previous.getMovementSpeed() * .75) return FlowBehaviorState.SLOWING_FLOW;
        if ((request.densityChange() != null && request.densityChange() >= .20)
                || (previous != null && request.movementSpeed() > previous.getMovementSpeed() * 1.15)) return FlowBehaviorState.RISING_FLOW;
        return FlowBehaviorState.NORMAL_FLOW;
    }

    public static FlowBehaviorState resolveWithHysteresis(List<RiskEvent> history, Instant timestamp,
                                                           FlowBehaviorState candidate) {
        if (candidate == FlowBehaviorState.INSUFFICIENT_DATA) return FlowBehaviorState.INSUFFICIENT_DATA;
        RiskEvent previous = history.isEmpty() ? null : history.get(history.size() - 1);
        FlowBehaviorState previousState = previous == null ? FlowBehaviorState.INSUFFICIENT_DATA : previous.getBehaviorState();
        if (previousState == null) previousState = FlowBehaviorState.INSUFFICIENT_DATA;
        if (previousState == FlowBehaviorState.INSUFFICIENT_DATA) {
            if (candidate == FlowBehaviorState.NORMAL_FLOW) return candidate;
            int samples = 1;
            Instant start = timestamp;
            for (int index = history.size() - 1; index >= 0; index--) {
                RiskEvent event = history.get(index);
                if (!matchesCandidate(event, candidate)) break;
                samples++;
                start = event.getTimestamp();
            }
            return samples >= 3 && Duration.between(start, timestamp).getSeconds() >= MIN_STATE_DURATION_SECONDS
                    ? candidate : FlowBehaviorState.INSUFFICIENT_DATA;
        }
        if (previousState == candidate) return candidate;
        int samples = 1;
        Instant start = timestamp;
        for (int index = history.size() - 1; index >= 0; index--) {
            RiskEvent event = history.get(index);
            if (event.getBehaviorState() != candidate) break;
            samples++;
            start = event.getTimestamp();
        }
        return samples >= 2 && Duration.between(start, timestamp).getSeconds() >= MIN_STATE_DURATION_SECONDS
                ? candidate : previousState;
    }

    private static String explanation(FlowBehaviorState state, FlowBehaviorState candidate, double confidence,
                                      double reverse, double conflicting, RiskEventRequest request) {
        if (state == FlowBehaviorState.INSUFFICIENT_DATA) {
            return "Insufficient tracked-person movement for a reliable direction or behavior state.";
        }
        String held = state != candidate ? " State held until the candidate persists for at least 10 seconds." : "";
        return switch (state) {
            case NORMAL_FLOW -> "Tracked people show a consistent " + request.dominantDirection() + " flow with no sustained reversal." + held;
            case RISING_FLOW -> "Flow activity is increasing while density or movement rises." + held;
            case SLOWING_FLOW -> "Movement is slowing relative to the recent baseline." + held;
            case REVERSE_FLOW -> "At least " + percent(reverse) + " of tracked movement is opposite the dominant direction." + held;
            case CONFLICTING_FLOW -> "Crossing movement is elevated at " + percent(conflicting) + ", indicating conflicting flow." + held;
            case UNUSUAL_BEHAVIOR -> "Movement differs materially from the recent stable pattern." + held;
            case INSUFFICIENT_DATA -> "Insufficient tracked-person movement for a reliable direction or behavior state.";
        };
    }

    private static double finiteOrZero(Double value) { return value != null && Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
    private static double clamp(Double value) { return finiteOrZero(value); }
    private static String percent(double value) { return Math.round(value * 100) + "%"; }

    private static boolean matchesCandidate(RiskEvent event, FlowBehaviorState candidate) {
        if (event.getBehaviorState() == candidate) return true;
        return switch (candidate) {
            case REVERSE_FLOW -> event.getReverseMovementRatio() >= REVERSE_THRESHOLD;
            case CONFLICTING_FLOW -> event.getConflictingMovementRatio() >= CONFLICTING_THRESHOLD;
            default -> false;
        };
    }

    public record Analysis(FlowBehaviorState state, String explanation, double confidence,
                           double reverseMovementRatio, double conflictingMovementRatio) { }
}
