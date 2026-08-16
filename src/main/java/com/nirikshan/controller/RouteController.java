package com.nirikshan.controller;

import com.nirikshan.dto.CitizenRouteGuidanceResponse;
import com.nirikshan.dto.RouteRecommendationResponse;
import com.nirikshan.dto.VenueGraphResponse;
import com.nirikshan.model.User;
import com.nirikshan.model.UserRole;
import com.nirikshan.security.CurrentUser;
import com.nirikshan.service.RouteRecommendationService;
import com.nirikshan.service.VenueGraphService;
import org.springframework.web.bind.annotation.*;

@RestController
public class RouteController {
    private final VenueGraphService graph;
    private final RouteRecommendationService routes;
    private final CurrentUser current;
    public RouteController(VenueGraphService graph, RouteRecommendationService routes, CurrentUser current) { this.graph = graph; this.routes = routes; this.current = current; }

    @GetMapping("/api/venues/{venueId}/route-graph")
    public VenueGraphResponse graph(@PathVariable Long venueId) { requireAuthority(); return graph.response(venueId); }

    @GetMapping("/api/venues/{venueId}/route-recommendation")
    public RouteRecommendationResponse recommendation(@PathVariable Long venueId, @RequestParam(required = false) Long originZoneId) {
        User user = requireAuthority();
        if (user.getRole() == UserRole.SECURITY && originZoneId != null && (user.getAssignedZone() == null || !user.getAssignedZone().getId().equals(originZoneId))) {
            throw new IllegalArgumentException("Route recommendation is outside your assigned zone");
        }
        return routes.recommend(venueId, originZoneId);
    }

    @GetMapping("/api/venue/routes")
    public CitizenRouteGuidanceResponse citizen(@RequestParam Long venueId, @RequestParam(required = false) Long originZoneId) {
        return routes.citizen(venueId, originZoneId);
    }

    private User requireAuthority() {
        User user = current.get();
        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.SECURITY) throw new IllegalArgumentException("Detailed route data is not available to citizen accounts");
        return user;
    }
}
