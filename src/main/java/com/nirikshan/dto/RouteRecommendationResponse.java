package com.nirikshan.dto;

import java.time.Instant;
import java.util.List;

public record RouteRecommendationResponse(
        Long venueId,
        Long originZoneId,
        RouteOption recommendedRoute,
        List<RouteOption> rejectedRoutes,
        String reason,
        int expectedTravelTimeSeconds,
        double riskScore,
        String gateAction,
        String gateActionReason,
        Instant generatedAt,
        String source,
        RouteBlockageResponse blockage,
        GateActionResponse gateActionDetail) {
    public RouteRecommendationResponse(Long venueId, Long originZoneId, RouteOption recommendedRoute,
                                       List<RouteOption> rejectedRoutes, String reason, int expectedTravelTimeSeconds,
                                       double riskScore, String gateAction, String gateActionReason,
                                       Instant generatedAt, String source) {
        this(venueId, originZoneId, recommendedRoute, rejectedRoutes, reason, expectedTravelTimeSeconds, riskScore,
                gateAction, gateActionReason, generatedAt, source,
                new RouteBlockageResponse("UNKNOWN", "Route evidence is not available.", List.of(), source),
                new GateActionResponse(com.nirikshan.model.GateActionType.NO_CHANGE, gateActionReason, null, 0,
                        com.nirikshan.model.RiskEventSource.valueOf(source)));
    }
    public record RouteOption(String routeId, String routeName, List<String> nodeLabels, String exitOrGate,
                              int expectedTravelTimeSeconds, int capacity, boolean open, boolean blocked,
                              boolean directionCompatible, double riskScore, String reason) { }
}
