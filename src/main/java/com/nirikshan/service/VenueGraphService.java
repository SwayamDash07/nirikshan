package com.nirikshan.service;

import com.nirikshan.dto.VenueGraphResponse;
import com.nirikshan.model.Venue;
import com.nirikshan.model.Zone;
import com.nirikshan.repository.VenueRepository;
import com.nirikshan.repository.ZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds a small local graph from venue/zone coordinates; no external map service is used. */
@Service
public class VenueGraphService {
    public static final String MAIN_GATE = "MAIN_GATE";
    public static final String MAIN_GATE_EXIT = "MAIN_GATE_EXIT";

    private final VenueRepository venues;
    private final ZoneRepository zones;
    private final int defaultCapacity;
    private final int mainGateExitCapacity;

    public VenueGraphService(VenueRepository venues, ZoneRepository zones,
                             @Value("${nirikshan.routes.default-capacity:600}") int defaultCapacity,
                             @Value("${nirikshan.routes.main-gate-exit-capacity:450}") int mainGateExitCapacity) {
        this.venues = venues; this.zones = zones; this.defaultCapacity = defaultCapacity; this.mainGateExitCapacity = mainGateExitCapacity;
    }

    public Graph graph(Long venueId) {
        Venue venue = venues.findById(venueId).orElseThrow(() -> new ResourceNotFoundException("Venue", venueId));
        List<Zone> zoneList = zones.findByVenueId(venueId);
        List<VenueGraphResponse.RouteNodeResponse> nodes = new ArrayList<>();
        List<VenueGraphResponse.RoutePathResponse> paths = new ArrayList<>();
        double baseLat = venue.getLatitude() == null ? zoneList.stream().map(Zone::getLatitude).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0) : venue.getLatitude();
        double baseLng = venue.getLongitude() == null ? zoneList.stream().map(Zone::getLongitude).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0) : venue.getLongitude();
        Zone mainGate = zoneList.stream().filter(zone -> zone.getName().toLowerCase().contains("main gate")).findFirst().orElse(zoneList.isEmpty() ? null : zoneList.get(0));
        Zone mainGateExit = zoneList.stream().filter(zone -> zone.getName().toLowerCase().contains("main gate exit")).findFirst()
                .orElseGet(() -> zoneList.stream().filter(zone -> zone.getName().toLowerCase().contains("c block exit gate")).findFirst().orElse(null));
        double mainGateLat = coordinate(mainGate == null ? null : mainGate.getLatitude(), baseLat);
        double mainGateLng = coordinate(mainGate == null ? null : mainGate.getLongitude(), baseLng);
        double mainGateExitLat = coordinate(mainGateExit == null ? null : mainGateExit.getLatitude(), baseLat - .00035);
        double mainGateExitLng = coordinate(mainGateExit == null ? null : mainGateExit.getLongitude(), baseLng + .00055);
        nodes.add(new VenueGraphResponse.RouteNodeResponse(MAIN_GATE, "Main Gate", "ENTRANCE", null, mainGateLat, mainGateLng));
        nodes.add(new VenueGraphResponse.RouteNodeResponse(MAIN_GATE_EXIT, "Main Gate Exit", "EXIT", mainGateExit == null ? null : mainGateExit.getId(), mainGateExitLat, mainGateExitLng));
        for (Zone zone : zoneList) {
            String nodeId = zoneNode(zone.getId());
            nodes.add(new VenueGraphResponse.RouteNodeResponse(nodeId, zone.getName(), "ZONE", zone.getId(), zone.getLatitude(), zone.getLongitude()));
            int timeToExit = travelSeconds(zone.getLatitude(), zone.getLongitude(), mainGateExitLat, mainGateExitLng);
            paths.add(path(nodeId + "_TO_MAIN_GATE_EXIT", nodeId, MAIN_GATE_EXIT, mainGateExitCapacity, timeToExit, "OUTBOUND", zone));
        }
        // Advisory monitored-zone corridors create alternate *route* candidates. They are not a claim
        // that a physical corridor is open; the action layer labels them for staff verification.
        for (Zone zone : zoneList) {
            zoneList.stream().filter(other -> !other.getId().equals(zone.getId()))
                    .min(Comparator.comparingInt(other -> travelSeconds(zone.getLatitude(), zone.getLongitude(), other.getLatitude(), other.getLongitude())))
                    .ifPresent(nearest -> {
                        String from = zoneNode(zone.getId()); String to = zoneNode(nearest.getId());
                        int seconds = travelSeconds(zone.getLatitude(), zone.getLongitude(), nearest.getLatitude(), nearest.getLongitude());
                        paths.add(path(from + "_TO_" + to, from, to, defaultCapacity, seconds, "BIDIRECTIONAL", zone));
                    });
        }
        if (mainGate != null) paths.add(path(MAIN_GATE + "_TO_" + zoneNode(mainGate.getId()), MAIN_GATE, zoneNode(mainGate.getId()), defaultCapacity, 20, "INBOUND", mainGate));
        return new Graph(venue, zoneList, nodes, paths);
    }

    public VenueGraphResponse response(Long venueId) {
        Graph graph = graph(venueId);
        return new VenueGraphResponse(venueId, graph.nodes(), graph.paths());
    }

    public static String zoneNode(Long zoneId) { return "ZONE_" + zoneId; }
    private static double coordinate(Double value, double fallback) { return value == null ? fallback : value; }
    private VenueGraphResponse.RoutePathResponse path(String id, String from, String to, int capacity, int seconds, String direction, Zone zone) {
        boolean blocked = zone != null && (zone.isBottleneckDetected() || zone.getCurrentRiskLevel() == com.nirikshan.model.RiskLevel.CRITICAL);
        return new VenueGraphResponse.RoutePathResponse(id, from, to, Math.max(1, capacity), Math.max(1, seconds), direction, !blocked, blocked);
    }
    public static int travelSeconds(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return 90;
        double latMeters = (lat2 - lat1) * 111_000;
        double lngMeters = (lng2 - lng1) * 111_000 * Math.cos(Math.toRadians((lat1 + lat2) / 2));
        return Math.max(15, (int) Math.round(Math.hypot(latMeters, lngMeters) / 1.4));
    }

    public record Graph(Venue venue, List<Zone> zones, List<VenueGraphResponse.RouteNodeResponse> nodes,
                        List<VenueGraphResponse.RoutePathResponse> paths) { }
}
