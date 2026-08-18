package com.nirikshan.service;

import com.nirikshan.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InterventionRuleEngineTest {
    @Test void oneWayNeedsPersistenceCongestionAndAlternateRoute() {
        var noAlternate = InterventionRuleEngine.oneWay(4, .55, "DEGRADED", false, "OUTBOUND", RiskEventSource.LIVE);
        var noisy = InterventionRuleEngine.oneWay(1, .20, "DEGRADED", true, "OUTBOUND", RiskEventSource.LIVE);
        var sustained = InterventionRuleEngine.oneWay(4, .55, "DEGRADED", true, "OUTBOUND", RiskEventSource.LIVE);
        assertFalse(noAlternate.recommended());
        assertFalse(noisy.recommended());
        assertTrue(sustained.recommended());
        assertEquals("OUTBOUND", sustained.direction());
        assertEquals(15, sustained.durationMinutes());
    }

    @Test void barricadeRulesAreExplicitAndAdvisory() {
        assertEquals("CLOSE_BARRICADE_GAP", InterventionRuleEngine.barricadeInstruction(false, false, false, "BLOCKED"));
        assertEquals("REMOVE_CROSSFLOW", InterventionRuleEngine.barricadeInstruction(false, true, true, "DEGRADED"));
        assertEquals("CREATE_ONE_WAY_CHANNEL", InterventionRuleEngine.barricadeInstruction(false, true, false, "DEGRADED"));
        assertEquals("NARROW_ENTRY", InterventionRuleEngine.barricadeInstruction(true, false, false, "OPEN"));
        assertEquals("OPEN_BARRICADE_GAP", InterventionRuleEngine.barricadeInstruction(false, false, false, "DEGRADED"));
    }

    @Test void gateActionsFollowRouteEvidence() {
        assertEquals(GateActionType.TEMPORARILY_CLOSE_EXIT, InterventionRuleEngine.gateAction(false, false, "BLOCKED", RiskLevel.HIGH));
        assertEquals(GateActionType.OPEN_ALTERNATE_EXIT, InterventionRuleEngine.gateAction(true, true, "DEGRADED", RiskLevel.MEDIUM));
        assertEquals(GateActionType.CLOSE_ENTRY_GATE, InterventionRuleEngine.gateAction(true, false, "DEGRADED", RiskLevel.HIGH));
        assertEquals(GateActionType.KEEP_GATE_OPEN, InterventionRuleEngine.gateAction(true, false, "OPEN", RiskLevel.LOW));
    }

    @Test void actionPrioritySupportsDeduplicatedEscalation() {
        assertTrue(RecommendationService.actionPriority(RecommendationType.ONE_WAY_FLOW)
                > RecommendationService.actionPriority(RecommendationType.REDIRECT));
        assertTrue(RecommendationService.actionPriority(RecommendationType.CLOSE_BARRICADE_GAP)
                > RecommendationService.actionPriority(RecommendationType.ONE_WAY_FLOW));
    }

    @Test void simulationIsRetainedInOneWayDecision() {
        var decision = InterventionRuleEngine.oneWay(3, .50, "DEGRADED", true, "NORTHBOUND", RiskEventSource.SIMULATION);
        assertTrue(decision.recommended());
        assertEquals(RiskEventSource.SIMULATION, decision.source());
    }
}
