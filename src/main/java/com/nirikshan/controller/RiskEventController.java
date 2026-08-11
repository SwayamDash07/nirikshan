package com.nirikshan.controller;
import com.nirikshan.dto.*;
import com.nirikshan.service.RiskEventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class RiskEventController {
    private final RiskEventService service;
    public RiskEventController(RiskEventService service) { this.service = service; }
    @PostMapping("/api/risk-events") public RiskEventResponse ingest(@Valid @RequestBody RiskEventRequest request) { return service.ingest(request); }
    @GetMapping("/api/zones/{zoneId}/risk-events") public List<RiskEventResponse> recent(@PathVariable Long zoneId, @RequestParam(defaultValue = "50") int limit) { return service.recent(zoneId, limit); }
}
