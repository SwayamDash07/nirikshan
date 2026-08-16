package com.nirikshan.service;

import com.nirikshan.dto.VenueGraphResponse;
import com.nirikshan.model.Venue;
import com.nirikshan.model.Zone;
import com.nirikshan.repository.VenueRepository;
import com.nirikshan.repository.ZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Builds a small local graph from venue/zone coordinates; no external map service is used. */
@Service
public class VenueGraphService {
    public static final String EXIT_A = "EXIT_A";
    public static final String EXIT_B = "EXIT_B";
    public static final String MAIN_GATE = "MAIN_GATE";

    private final VenueRepository venues;
    private final ZoneRepository zones;
    private final int defaultCapacity;
    private final int exitBCapacity;

    public VenueGraphService(VenueRepository venues, ZoneRepository zones,
                             @Value("${nirikshan.routes.default-capacity:600}") int defaultCapacity,
                             @Value("${nirikshan.routes.exit-b-capacity:450}") int exitBCapacity) {
        this.venues = venues; this.zones = zones; this.defaultCapacity = defaultCapacity; this.exitBCapacity = exitBCapacity;
    }

    public Graph graph(Long venueId) {
        Venue venue = venues.findById(venueId).orElseThrow(() -> new ResourceNotFoundException("Venue", venueId));
        List<Zone> zoneList = zones.findByVenueId(venueId);
        List<VenueGraphResponse.RouteNodeResponse> nodes = new ArrayList<>();
        List<VenueGraphResponse.RoutePathResponse> paths = new ArrayList<>();
        nodes.add(new VenueGraphResponse.RouteNodeResponse(MAIN_GATE, "Main Gate", "GATE", null, venue.getLatitude(), venue.getLongitude()));
        double baseLat = venue.getLatitude() == null ? zoneList.stream().map(Zone::getLatitude).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0) : venue.getLatitude();
        double baseLng = venue.getLongitude() == null ? zoneList.stream().map(Zone::getLongitude).filter(v -> v != null).mapToDouble(Double::doubleValue).average().orElse(0) : venue.getLongitude();
        nodes.add(new VenueGraphResponse.RouteNodeResponse(EXIT_A, "Exit A", "EXIT", null, baseLat + .00045, baseLng - .00035));
        nodes.add(new VenueGraphResponse.RouteNodeResponse(EXIT_B, "Exit B", "EXIT", null, baseLat - .00035, baseLng + .00055));
        Zone mainGate = zoneList.stream().filter(zone -> zone.getName().toLowerCase().contains("main gate")).findFirst().orElse(zoneList.isEmpty() ? null : zoneList.get(0));
        for (Zone zone : zoneList) {
            String nodeId = zoneNode(zone.getId());
            nodes.add(new VenueGraphResponse.RouteNodeResponse(nodeId, zone.getName(), "ZONE", zone.getId(), zone.getLatitude(), zone.getLongitude()));
            int timeA = travelSeconds(zone.getLatitude(), zone.getLongitude(), baseLat + .00045, baseLng - .00035);
            int timeB = travelSeconds(zone.getLatitude(), zone.getLongitude(), baseLat - .00035, baseLng + .00055);
            paths.add(path(nodeId + "_TO_EXIT_A", nodeId, EXIT_A, defaultCapacity, timeA, "OUTBOUND"));
            paths.add(path(nodeId + "_TO_EXIT_B", nodeId, EXIT_B, exitBCapacity, timeB, "OUTBOUND"));
            paths.add(path(nodeId + "_TO_MAIN_GATE", nodeId, MAIN_GATE, defaultCapacity, travelSeconds(zone.getLatitude(), zone.getLongitude(), baseLat, baseLng), "OUTBOUND"));
        }
        if (mainGate != null) paths.add(path(MAIN_GATE + "_TO_" + zoneNode(mainGate.getId()), MAIN_GATE, zoneNode(mainGate.getId()), defaultCapacity, 20, "INBOUND"));
        return new Graph(venue, zoneList, nodes, paths);
    }

    public VenueGraphResponse response(Long venueId) {
        Graph graph = graph(venueId);
        return new VenueGraphResponse(venueId, graph.nodes(), graph.paths());
    }

    public static String zoneNode(Long zoneId) { return "ZONE_" + zoneId; }
    private VenueGraphResponse.RoutePathResponse path(String id, String from, String to, int capacity, int seconds, String direction) {
        return new VenueGraphResponse.RoutePathResponse(id, from, to, Math.max(1, capacity), Math.max(1, seconds), direction, true, false);
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
