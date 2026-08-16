package com.nirikshan.service;

import com.nirikshan.dto.CitizenRouteGuidanceResponse;
import com.nirikshan.dto.RouteRecommendationResponse;
import com.nirikshan.dto.VenueGraphResponse;
import com.nirikshan.model.FlowBehaviorState;
import com.nirikshan.model.RiskEvent;
import com.nirikshan.model.RiskEventSource;
import com.nirikshan.model.RiskLevel;
import com.nirikshan.model.Zone;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.ZoneRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Lowest-score route selection using only local graph and current forecast signals. */
@Service
public class RouteRecommendationService {
    private final VenueGraphService graphService;
    private final ZoneRepository zones;
    private final RiskEventRepository events;
    private final RiskForecastService forecasts;

    public RouteRecommendationService(VenueGraphService graphService, ZoneRepository zones,
                                      RiskEventRepository events, RiskForecastService forecasts) {
        this.graphService = graphService; this.zones = zones; this.events = events; this.forecasts = forecasts;
    }

    public RouteRecommendationResponse recommend(Long venueId, Long originZoneId) {
        VenueGraphService.Graph graph = graphService.graph(venueId);
        Zone origin = originZoneId == null
                ? graph.zones().stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Zone", 0L))
                : zones.findById(originZoneId).orElseThrow(() -> new ResourceNotFoundException("Zone", originZoneId));
        if (!origin.getVenue().getId().equals(venueId)) throw new IllegalArgumentException("Origin zone is outside the selected venue");
        RiskEvent latest = events.findByZoneIdOrderByTimestampDesc(origin.getId(), PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        var forecast = forecasts.forecast(origin.getId());
        FlowBehaviorState behavior = latest == null ? FlowBehaviorState.INSUFFICIENT_DATA : latest.getBehaviorState();
        boolean simulatedPrimaryRouteBlocked = latest != null && latest.getSourceClipId() != null
                && latest.getSourceClipId().toUpperCase().contains("BLOCKED_ROUTE");
        List<RouteRecommendationResponse.RouteOption> options = new ArrayList<>();
        for (String exit : List.of(VenueGraphService.C_BLOCK_GATE)) {
            VenueGraphResponse.RoutePathResponse path = graph.paths().stream()
                    .filter(item -> item.fromNodeId().equals(VenueGraphService.zoneNode(origin.getId())) && item.toNodeId().equals(exit))
                    .findFirst().orElseThrow();
            boolean blocked = simulatedPrimaryRouteBlocked
                    || origin.isBottleneckDetected()
                    || origin.getCurrentRiskLevel() == RiskLevel.CRITICAL;
            boolean directionCompatible = behavior != FlowBehaviorState.REVERSE_FLOW
                    && behavior != FlowBehaviorState.CONFLICTING_FLOW;
            double score = score(origin.getCurrentRiskLevel(), forecast.projectedRisk(), origin.isBottleneckDetected(),
                    path.capacity(), origin.getCurrentPeopleCount(), blocked, directionCompatible, path.travelTimeSeconds());
            String reason = blocked ? "Route is blocked by the current bottleneck/critical state."
                    : !directionCompatible ? "Allowed direction conflicts with observed movement."
                    : "Higher combined risk, capacity pressure, or travel time than the selected route.";
            options.add(new RouteRecommendationResponse.RouteOption(
                    path.id(), "Route to " + label(exit) + " via " + origin.getName(),
                    List.of(origin.getName(), label(exit)), label(exit), path.travelTimeSeconds(), path.capacity(),
                    path.open() && !blocked, blocked, directionCompatible, round(score), reason));
        }
        options.sort(Comparator.comparingDouble(RouteRecommendationResponse.RouteOption::riskScore)
                .thenComparingInt(RouteRecommendationResponse.RouteOption::expectedTravelTimeSeconds));
        RouteRecommendationResponse.RouteOption selected = options.stream().filter(item -> item.open() && !item.blocked() && item.directionCompatible()).findFirst().orElse(null);
        List<RouteRecommendationResponse.RouteOption> rejected = options.stream().filter(item -> item != selected).toList();
        String gateAction;
        String gateReason;
        if (selected == null) {
            gateAction = "KEEP_MAIN_GATE_CLOSED";
            gateReason = "No open route to the designated C Block Gate exit is currently available.";
        } else if (selected.exitOrGate().equals("C Block Gate") && origin.getCurrentRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal()) {
            gateAction = "OPEN_C_BLOCK_GATE";
            gateReason = "C Block Gate is the designated outbound gate for this venue.";
        } else if (origin.getCurrentRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal()) {
            gateAction = "CLOSE_" + origin.getName().toUpperCase().replace(' ', '_');
            gateReason = "Reduce inflow while the origin zone is high risk.";
        } else {
            gateAction = "KEEP_GATES_OPEN";
            gateReason = "No gate change is required for the current score.";
        }
        String reason = selected == null ? "No safe route to C Block Gate is currently available; keep the Main Gate for entry only." :
                "Selected " + selected.routeName() + " because it has the lowest non-blocked route score.";
        return new RouteRecommendationResponse(venueId, origin.getId(), selected, rejected, reason,
                selected == null ? 0 : selected.expectedTravelTimeSeconds(), selected == null ? 1 : selected.riskScore(),
                gateAction, gateReason, Instant.now(), latest == null ? RiskEventSource.LIVE.name() : latest.getSource().name());
    }

    public CitizenRouteGuidanceResponse citizen(Long venueId, Long originZoneId) {
        RouteRecommendationResponse detailed = recommend(venueId, originZoneId);
        var route = detailed.recommendedRoute();
        if (route == null) return new CitizenRouteGuidanceResponse(venueId, detailed.originZoneId(), "No safe route available",
                citizenMessage("C Block Gate", false), "C Block Gate", 0, false, detailed.generatedAt(), detailed.source());
        String guidance = citizenMessage(route.exitOrGate(), true);
        if (detailed.gateAction().equals("OPEN_C_BLOCK_GATE")) guidance = "Use C Block Gate.";
        return new CitizenRouteGuidanceResponse(venueId, detailed.originZoneId(), route.routeName(), guidance,
                route.exitOrGate(), route.expectedTravelTimeSeconds(), true, detailed.generatedAt(), detailed.source());
    }

    public static double score(RiskLevel currentRisk, RiskLevel projectedRisk, boolean bottleneck,
                               int capacity, int people, boolean blocked, boolean directionCompatible,
                               int travelTimeSeconds) {
        if (blocked) return 1.0;
        double current = currentRisk.ordinal() / 3.0;
        double projected = projectedRisk.ordinal() / 3.0;
        double pressure = Math.min(1.0, people / (double) Math.max(1, capacity));
        double travel = Math.min(1.0, travelTimeSeconds / 300.0);
        double directionPenalty = directionCompatible ? 0 : 1;
        return Math.min(1.0, .30 * current + .25 * projected + .15 * (bottleneck ? 1 : 0)
                + .15 * pressure + .10 * travel + .05 * directionPenalty);
    }

    public static String citizenMessage(String exitOrGate, boolean available) {
        if (!available) return "C Block Gate is unavailable; keep Main Gate for entry and follow staff directions.";
        return switch (exitOrGate) {
            case "C Block Gate" -> "Use C Block Gate.";
            default -> "Use the alternate route shown.";
        };
    }

    private static String label(String id) { return id.equals(VenueGraphService.C_BLOCK_GATE) ? "C Block Gate" : "Main Gate"; }
    private static double round(double value) { return Math.round(value * 100.0) / 100.0; }
}
