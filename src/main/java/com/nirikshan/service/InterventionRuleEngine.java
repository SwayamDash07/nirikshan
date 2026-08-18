package com.nirikshan.service;

import com.nirikshan.model.*;

/** Pure deterministic intervention rules. They produce recommendations, never device commands. */
public final class InterventionRuleEngine {
    private InterventionRuleEngine() { }
    public static OneWayDecision oneWay(int sustainedConflictReadings, double reverseMovement, String routeStatus,
                                        boolean alternateAvailable, String dominantDirection, RiskEventSource source) {
        boolean conflictSustained = sustainedConflictReadings >= 3;
        boolean reverseElevated = reverseMovement >= FlowBehaviorService.REVERSE_THRESHOLD;
        boolean congested = "DEGRADED".equals(routeStatus) || "BLOCKED".equals(routeStatus);
        if (!alternateAvailable || !congested || (!conflictSustained && !reverseElevated)) return OneWayDecision.none(source);
        String direction = dominantDirection == null || dominantDirection.isBlank() ? "OUTBOUND" : dominantDirection.toUpperCase();
        RiskLevel severity = "BLOCKED".equals(routeStatus) || reverseMovement >= .65 ? RiskLevel.HIGH : RiskLevel.MEDIUM;
        double confidence = Math.min(.95, .35 + (conflictSustained ? .25 : 0) + Math.min(.25, reverseMovement * .35) + ("BLOCKED".equals(routeStatus) ? .15 : .05));
        String reason = (conflictSustained ? "conflicting movement persisted across multiple readings" : "reverse movement is elevated")
                + ", the route is " + routeStatus.toLowerCase() + ", and an alternate route is available.";
        return new OneWayDecision(true, direction, reason, 15, severity, confidence, source);
    }
    public static String barricadeInstruction(boolean bottleneck, boolean crossflow, boolean reverse, String routeStatus) {
        if ("BLOCKED".equals(routeStatus)) return "CLOSE_BARRICADE_GAP";
        if (crossflow && reverse) return "REMOVE_CROSSFLOW";
        if (crossflow) return "CREATE_ONE_WAY_CHANNEL";
        if (bottleneck) return "NARROW_ENTRY";
        if ("DEGRADED".equals(routeStatus)) return "OPEN_BARRICADE_GAP";
        return null;
    }
    public static GateActionType gateAction(boolean routeAvailable, boolean alternateAvailable, String routeStatus, RiskLevel originRisk) {
        if (!routeAvailable && "BLOCKED".equals(routeStatus)) return GateActionType.TEMPORARILY_CLOSE_EXIT;
        if (alternateAvailable && ("BLOCKED".equals(routeStatus) || "DEGRADED".equals(routeStatus))) return GateActionType.OPEN_ALTERNATE_EXIT;
        if (originRisk.ordinal() >= RiskLevel.HIGH.ordinal()) return GateActionType.CLOSE_ENTRY_GATE;
        if (routeAvailable && "OPEN".equals(routeStatus)) return GateActionType.KEEP_GATE_OPEN;
        return GateActionType.NO_CHANGE;
    }
    public record OneWayDecision(boolean recommended, String direction, String reason, int durationMinutes,
                                 RiskLevel severity, double confidence, RiskEventSource source) {
        static OneWayDecision none(RiskEventSource source) { return new OneWayDecision(false, null, "Conditions do not justify a one-way recommendation.", 0, RiskLevel.LOW, 0, source); }
    }
}
