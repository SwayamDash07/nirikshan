package com.nirikshan.controller;
import com.nirikshan.model.*;
import com.nirikshan.repository.*;
import com.nirikshan.service.ResourceNotFoundException;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/venues")
public class VenueController {
    private final VenueRepository venueRepository;
    private final ZoneRepository zoneRepository;
    public VenueController(VenueRepository venueRepository, ZoneRepository zoneRepository) { this.venueRepository = venueRepository; this.zoneRepository = zoneRepository; }
    @GetMapping public List<Venue> list() { return venueRepository.findAll(); }
    @GetMapping("/{id}/zones") public List<Zone> zones(@PathVariable Long id) {
        if (!venueRepository.existsById(id)) throw new ResourceNotFoundException("Venue", id);
        return zoneRepository.findByVenueId(id);
    }
}
