package com.nirikshan.dto;

import java.util.List;

public record VenueGraphResponse(Long venueId, List<RouteNodeResponse> nodes, List<RoutePathResponse> paths) {
    public record RouteNodeResponse(String id, String label, String kind, Long zoneId, Double latitude, Double longitude) { }
    public record RoutePathResponse(String id, String fromNodeId, String toNodeId, int capacity,
                                    int travelTimeSeconds, String allowedDirection, boolean open, boolean blocked) { }
}
