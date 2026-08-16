package com.nirikshan.service;

import com.nirikshan.dto.RiskEventRequest;
import com.nirikshan.model.FlowBehaviorState;
import com.nirikshan.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlowAndRouteIntelligenceTest {
    @Test void reverseAndConflictingCandidatesAreExplainable() {
        RiskEventRequest reverse = request(.80, .60, FlowBehaviorState.REVERSE_FLOW);
        assertEquals(FlowBehaviorState.REVERSE_FLOW, FlowBehaviorService.candidate(reverse, .8, .6, .02, null));
        RiskEventRequest conflicting = request(.80, .35, FlowBehaviorState.CONFLICTING_FLOW);
        assertEquals(FlowBehaviorState.CONFLICTING_FLOW, FlowBehaviorService.candidate(conflicting, .8, .1, .35, null));
    }

    @Test void behaviorHysteresisRequiresTwoSamplesAndTenSeconds() {
        var first = event(0, FlowBehaviorState.NORMAL_FLOW);
        var history = List.of(first);
        assertEquals(FlowBehaviorState.NORMAL_FLOW, FlowBehaviorService.resolveWithHysteresis(history, Instant.parse("2026-01-01T00:00:05Z"), FlowBehaviorState.REVERSE_FLOW));
        var second = event(5, FlowBehaviorState.REVERSE_FLOW);
        assertEquals(FlowBehaviorState.REVERSE_FLOW, FlowBehaviorService.resolveWithHysteresis(List.of(first, second), Instant.parse("2026-01-01T00:00:15Z"), FlowBehaviorState.REVERSE_FLOW));
    }

    @Test void routeScoringAvoidsBlockedAndIncompatibleRoutes() {
        double blocked = RouteRecommendationService.score(RiskLevel.HIGH, RiskLevel.CRITICAL, true, 100, 90, true, true, 30);
        double open = RouteRecommendationService.score(RiskLevel.LOW, RiskLevel.MEDIUM, false, 600, 20, false, true, 60);
        assertEquals(1.0, blocked);
        assertTrue(open < blocked);
        assertEquals("Use C Block Gate.", RouteRecommendationService.citizenMessage("C Block Gate", true));
        assertEquals("C Block Gate is unavailable; keep Main Gate for entry and follow staff directions.", RouteRecommendationService.citizenMessage("C Block Gate", false));
    }

    private static RiskEventRequest request(double confidence, double reverse, FlowBehaviorState state) {
        return new RiskEventRequest(1L, Instant.parse("2026-01-01T00:00:00Z"), 1, 10, 1, RiskLevel.MEDIUM,
                "flow", List.of(), "test", 0.2, 0.1, 0.0, "SIMULATION", "E", 90.0, confidence,
                confidence, reverse, state == FlowBehaviorState.CONFLICTING_FLOW ? .4 : .05, state, "fixture");
    }

    private static com.nirikshan.model.RiskEvent event(long seconds, FlowBehaviorState state) {
        var event = new com.nirikshan.model.RiskEvent();
        event.setTimestamp(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(seconds));
        event.setBehaviorState(state);
        return event;
    }
}
