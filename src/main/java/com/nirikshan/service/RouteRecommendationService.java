package com.nirikshan.service;

import com.nirikshan.dto.CitizenRouteGuidanceResponse;
import com.nirikshan.dto.RouteRecommendationResponse;
import com.nirikshan.dto.VenueGraphResponse;
import com.nirikshan.model.FlowBehaviorState;
import com.nirikshan.model.GateActionType;
import com.nirikshan.model.RiskEvent;
import com.nirikshan.model.RiskEventSource;
import com.nirikshan.model.RiskLevel;
import com.nirikshan.model.Zone;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.ZoneRepository;
import com.nirikshan.repository.CitizenReportRepository;
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
    private final CitizenReportRepository reports;

    public RouteRecommendationService(VenueGraphService graphService, ZoneRepository zones,
                                      RiskEventRepository events, RiskForecastService forecasts, CitizenReportRepository reports) {
        this.graphService = graphService; this.zones = zones; this.events = events; this.forecasts = forecasts; this.reports = reports;
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
        boolean reportBlockage = reports.findByZone_IdOrderByTimestampDesc(origin.getId()).stream().limit(5)
                .filter(report -> report.getTimestamp() != null && latest != null && !report.getTimestamp().isBefore(latest.getTimestamp().minusSeconds(120)))
                .anyMatch(report -> report.getDescription() != null && report.getDescription().toLowerCase().matches(".*(blocked|closed|fire|trapped|emergency).*"));
        boolean blocked = simulatedPrimaryRouteBlocked || reportBlockage || origin.isBottleneckDetected() || origin.getCurrentRiskLevel() == RiskLevel.CRITICAL;
        String blockageStatus = routeStatus(blocked, latest == null || forecast.stale(), forecast.movementSlowdown(), origin.getCurrentRiskLevel());
        List<String> blockageEvidence = new ArrayList<>();
        if (simulatedPrimaryRouteBlocked) blockageEvidence.add("simulation marked this route blocked");
        if (reportBlockage) blockageEvidence.add("recent citizen report indicates a blocked or unsafe passage");
        if (origin.isBottleneckDetected()) blockageEvidence.add("persistent hotspot/bottleneck evidence");
        if (origin.getCurrentRiskLevel() == RiskLevel.CRITICAL) blockageEvidence.add("critical zone risk");
        if (forecast.movementSlowdown() >= .20) blockageEvidence.add("flow slowdown is " + Math.round(forecast.movementSlowdown() * 100) + "%");
        if (latest != null && latest.getSource() == RiskEventSource.SIMULATION) blockageEvidence.add("simulation signal");
        String blockageReason = blockageStatus.equals("BLOCKED") ? "Current route evidence indicates the route should not be used."
                : blockageStatus.equals("DEGRADED") ? "Route remains usable but capacity or movement is reduced."
                : blockageStatus.equals("UNKNOWN") ? "No fresh route evidence is available." : "No current blockage evidence is present.";
        List<RouteRecommendationResponse.RouteOption> options = new ArrayList<>();
        boolean directionCompatible = behavior != FlowBehaviorState.REVERSE_FLOW
                && behavior != FlowBehaviorState.CONFLICTING_FLOW;
        for (String exit : List.of(VenueGraphService.MAIN_GATE_EXIT)) {
            VenueGraphResponse.RoutePathResponse path = graph.paths().stream()
                    .filter(item -> item.fromNodeId().equals(VenueGraphService.zoneNode(origin.getId())) && item.toNodeId().equals(exit))
                    .findFirst().orElseThrow();
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
        // An alternate is a monitored-zone diversion that still reaches the designated exit. It is
        // intentionally labelled for staff verification, not treated as automatic field control.
        for (VenueGraphResponse.RoutePathResponse corridor : graph.paths().stream()
                .filter(path -> path.fromNodeId().equals(VenueGraphService.zoneNode(origin.getId())) && path.toNodeId().startsWith("ZONE_"))
                .toList()) {
            String viaNode = corridor.toNodeId();
            VenueGraphResponse.RoutePathResponse exitPath = graph.paths().stream()
                    .filter(path -> path.fromNodeId().equals(viaNode) && path.toNodeId().equals(VenueGraphService.MAIN_GATE_EXIT)).findFirst().orElse(null);
            if (exitPath == null) continue;
            Zone via = graph.zones().stream().filter(zone -> VenueGraphService.zoneNode(zone.getId()).equals(viaNode)).findFirst().orElse(null);
            if (via == null) continue;
            boolean alternateBlocked = blocked || corridor.blocked() || exitPath.blocked();
            int seconds = corridor.travelTimeSeconds() + exitPath.travelTimeSeconds();
            double alternateScore = score(via.getCurrentRiskLevel(), forecasts.forecast(via.getId()).projectedRisk(), via.isBottleneckDetected(),
                    Math.min(corridor.capacity(), exitPath.capacity()), via.getCurrentPeopleCount(), alternateBlocked, directionCompatible, seconds);
            options.add(new RouteRecommendationResponse.RouteOption(corridor.id() + "_VIA_" + viaNode,
                    "Staff-verified diversion via " + via.getName(), List.of(origin.getName(), via.getName(), "Main Gate Exit"),
                    "Main Gate Exit", seconds, Math.min(corridor.capacity(), exitPath.capacity()), !alternateBlocked, alternateBlocked,
                    directionCompatible, round(alternateScore), alternateBlocked ? "Diversion route is currently constrained." : "Alternate monitored-zone diversion; staff must verify physical availability before directing people."));
        }
        options.sort(Comparator.comparingDouble(RouteRecommendationResponse.RouteOption::riskScore)
                .thenComparingInt(RouteRecommendationResponse.RouteOption::expectedTravelTimeSeconds));
        RouteRecommendationResponse.RouteOption selected = options.stream().filter(item -> item.open() && !item.blocked() && item.directionCompatible()).findFirst().orElse(null);
        List<RouteRecommendationResponse.RouteOption> rejected = options.stream().filter(item -> item != selected).toList();
        boolean alternateAvailable = options.stream().anyMatch(option -> selected != null && option != selected && option.open() && !option.blocked() && option.directionCompatible());
        GateActionType gateAction = InterventionRuleEngine.gateAction(selected != null, alternateAvailable, blockageStatus, origin.getCurrentRiskLevel());
        String gateReason = switch (gateAction) {
            case TEMPORARILY_CLOSE_EXIT -> "No safe outbound route is currently available; this is an advisory temporary closure, not automatic gate control.";
            case OPEN_ALTERNATE_EXIT -> "The primary route is constrained and an alternate route is available for staff verification.";
            case CLOSE_ENTRY_GATE -> "Reduce inbound pressure while the origin zone is high risk.";
            case KEEP_GATE_OPEN -> "The selected route is open with no current gate restriction evidence.";
            case NO_CHANGE -> "Current route evidence does not justify a gate change.";
        };
        String reason = selected == null ? "No safe route to Main Gate Exit is currently available; keep the Main Gate for entry only." :
                "Selected " + selected.routeName() + " because it has the lowest non-blocked route score.";
        return new RouteRecommendationResponse(venueId, origin.getId(), selected, rejected, reason,
                selected == null ? 0 : selected.expectedTravelTimeSeconds(), selected == null ? 1 : selected.riskScore(),
                gateAction.name(), gateReason, Instant.now(), latest == null ? RiskEventSource.LIVE.name() : latest.getSource().name(),
                new com.nirikshan.dto.RouteBlockageResponse(blockageStatus, blockageReason, List.copyOf(blockageEvidence), latest == null ? RiskEventSource.LIVE.name() : latest.getSource().name()),
                new com.nirikshan.dto.GateActionResponse(gateAction, gateReason, selected == null ? origin.getName() : selected.routeName(),
                        selected == null ? .70 : Math.max(.50, 1 - selected.riskScore()), latest == null ? RiskEventSource.LIVE : latest.getSource()));
    }

    public CitizenRouteGuidanceResponse citizen(Long venueId, Long originZoneId) {
        RouteRecommendationResponse detailed = recommend(venueId, originZoneId);
        var route = detailed.recommendedRoute();
        if (route == null) return new CitizenRouteGuidanceResponse(venueId, detailed.originZoneId(), "No safe route available",
                citizenMessage("Main Gate Exit", false), "Main Gate Exit", 0, false, detailed.generatedAt(), detailed.source());
        String guidance = citizenMessage(route.exitOrGate(), true);
        if (detailed.gateActionDetail().action() == GateActionType.OPEN_ALTERNATE_EXIT) guidance = "Follow staff directions to the approved alternate exit.";
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

    public static String routeStatus(boolean blockedEvidence, boolean unknown, double slowdown, RiskLevel risk) {
        if (blockedEvidence) return "BLOCKED";
        if (unknown) return "UNKNOWN";
        return slowdown >= .20 || risk.ordinal() >= RiskLevel.HIGH.ordinal() ? "DEGRADED" : "OPEN";
    }

    public static String citizenMessage(String exitOrGate, boolean available) {
        if (!available) return "Main Gate Exit is unavailable; keep Main Gate for entry and follow staff directions.";
        return switch (exitOrGate) {
            case "Main Gate Exit" -> "Use Main Gate Exit.";
            default -> "Use the alternate route shown.";
        };
    }

    private static String label(String id) { return id.equals(VenueGraphService.MAIN_GATE_EXIT) ? "Main Gate Exit" : "Main Gate"; }
    private static double round(double value) { return Math.round(value * 100.0) / 100.0; }
}
