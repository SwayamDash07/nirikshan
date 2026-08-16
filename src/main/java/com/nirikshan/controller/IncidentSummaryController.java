package com.nirikshan.controller;

import com.nirikshan.dto.IncidentSummaryResponse;
import com.nirikshan.service.IncidentSummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IncidentSummaryController {
    private final IncidentSummaryService summaries;

    public IncidentSummaryController(IncidentSummaryService summaries) { this.summaries = summaries; }

    @GetMapping("/api/zones/{id}/incident-summary")
    public IncidentSummaryResponse zone(@PathVariable Long id, @RequestParam(defaultValue = "en") String language) {
        return summaries.forZone(id, language);
    }

    @GetMapping("/api/venue/incident-summary")
    public IncidentSummaryResponse venue(@RequestParam(defaultValue = "en") String language) {
        return summaries.forVenue(language);
    }
}
