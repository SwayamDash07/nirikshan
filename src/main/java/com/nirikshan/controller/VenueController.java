package com.nirikshan.controller;
import com.nirikshan.model.*;
import com.nirikshan.repository.*;
import com.nirikshan.service.ResourceNotFoundException;
import com.nirikshan.service.RiskEventService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/venues")
public class VenueController {
    private final VenueRepository venueRepository;
    private final ZoneRepository zoneRepository;
    private final RiskEventService riskEvents;
    public VenueController(VenueRepository venueRepository, ZoneRepository zoneRepository, RiskEventService riskEvents) { this.venueRepository = venueRepository; this.zoneRepository = zoneRepository; this.riskEvents = riskEvents; }
    @GetMapping public List<VenueResponse> list() { return venueRepository.findAll().stream().map(VenueController::response).toList(); }
    @GetMapping("/{id}/zones") public List<Zone> zones(@PathVariable Long id) {
        if (!venueRepository.existsById(id)) throw new ResourceNotFoundException("Venue", id);
        return zoneRepository.findByVenueId(id);
    }
    @GetMapping("/{id}/risk-events") public List<com.nirikshan.dto.RiskEventResponse> riskEvents(@PathVariable Long id, @RequestParam(defaultValue = "120") int limit) { return riskEvents.recentVenue(id, limit); }
    private static VenueResponse response(Venue venue) { return new VenueResponse(venue.getId(), venue.getName(), venue.getDescription(), venue.getLatitude(), venue.getLongitude(), venue.getServiceRadiusMeters(), venue.getCreatedAt()); }
    public record VenueResponse(Long id, String name, String description, Double latitude, Double longitude, Double serviceRadiusMeters, java.time.Instant createdAt) {}
}
