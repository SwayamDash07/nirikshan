package com.nirikshan.service;

import com.nirikshan.model.RiskEvent;
import com.nirikshan.model.RiskEventSource;
import com.nirikshan.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RiskIntelligenceServiceTest {
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Test void stampedeScoreIsBoundedAndExplainsContributingSignals() {
        var service = new RiskIntelligenceService(null, null);
        var result = service.stampede(readings(), 6.5, 1.2, .55, 70, true, RiskEventSource.SIMULATION, null);
        assertTrue(result.score() >= 0 && result.score() <= 1);
        assertEquals("HIGH", result.level());
        assertTrue(result.evidence().stream().anyMatch(item -> item.contains("projected density")));
        assertTrue(result.explanation().contains("Heuristic score"));
    }

    @Test void unusualBehaviorNeedsTwoConsecutiveAtypicalReadings() {
        var service = new RiskIntelligenceService(null, null);
        var one = service.unusual(List.of(reading(0, 1, 1, 0, 0), reading(10, 1.2, .4, .6, 0), reading(20, 1.2, 1, 0, 0)));
        assertFalse(one.detected());
        var two = service.unusual(List.of(reading(0, 1, 1, 0, 0), reading(10, 1.2, .4, .6, 0), reading(20, 2.0, .2, .7, .4), reading(30, 3.0, .1, .7, .4)));
        assertTrue(two.detected());
        assertTrue(two.persistentReadings() >= 2);
    }

    @Test void propagationStateRequiresSourceAndDistinguishesCoordinatedWorsening() {
        assertEquals("NONE", RiskIntelligenceService.propagationState(false, 3));
        assertEquals("PROPAGATING", RiskIntelligenceService.propagationState(true, 1));
        assertEquals("COORDINATED_WORSENING", RiskIntelligenceService.propagationState(true, 2));
    }

    @Test void routeStatusIsDeterministicAndUnknownWhenEvidenceIsStale() {
        assertEquals("BLOCKED", RouteRecommendationService.routeStatus(true, false, 0, RiskLevel.LOW));
        assertEquals("UNKNOWN", RouteRecommendationService.routeStatus(false, true, .8, RiskLevel.CRITICAL));
        assertEquals("DEGRADED", RouteRecommendationService.routeStatus(false, false, .3, RiskLevel.MEDIUM));
        assertEquals("OPEN", RouteRecommendationService.routeStatus(false, false, .05, RiskLevel.LOW));
    }

    private static List<RiskEvent> readings() {
        List<RiskEvent> result = new ArrayList<>();
        result.add(reading(0, 2, .8, 0, 0)); result.add(reading(10, 3, .5, .5, .35));
        result.add(reading(20, 4.5, .3, .6, .4)); result.add(reading(30, 5.5, .2, .6, .4));
        return result;
    }
    private static RiskEvent reading(long seconds, double density, double speed, double reverse, double conflict) {
        var event = new RiskEvent(); event.setTimestamp(BASE.plusSeconds(seconds)); event.setDensityScore(density);
        event.setMovementSpeed(speed); event.setReverseMovementRatio(reverse); event.setConflictingMovementRatio(conflict);
        event.setMovementSlowdown(Math.max(0, 1 - speed)); event.setRiskLevel(density >= 4 ? RiskLevel.HIGH : RiskLevel.MEDIUM);
        event.setSource(RiskEventSource.SIMULATION); return event;
    }
}
