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
        String source) {
    public record RouteOption(String routeId, String routeName, List<String> nodeLabels, String exitOrGate,
                              int expectedTravelTimeSeconds, int capacity, boolean open, boolean blocked,
                              boolean directionCompatible, double riskScore, String reason) { }
}
