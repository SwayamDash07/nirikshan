package com.nirikshan.controller;

import com.nirikshan.dto.CitizenRiskForecastResponse;
import com.nirikshan.dto.RiskForecastResponse;
import com.nirikshan.model.UserRole;
import com.nirikshan.repository.VenueRepository;
import com.nirikshan.security.CurrentUser;
import com.nirikshan.service.RiskForecastService;
import com.nirikshan.service.ResourceNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class RiskForecastController {
    private final RiskForecastService forecasts;
    private final CurrentUser currentUser;
    private final VenueRepository venues;

    public RiskForecastController(RiskForecastService forecasts, CurrentUser currentUser, VenueRepository venues) {
        this.forecasts = forecasts; this.currentUser = currentUser; this.venues = venues;
    }

    @GetMapping("/api/zones/{zoneId}/risk-forecast")
    public RiskForecastResponse zone(@PathVariable Long zoneId) {
        var user = currentUser.get();
        if (user.getRole() == UserRole.SECURITY && (user.getAssignedZone() == null || !user.getAssignedZone().getId().equals(zoneId))) {
            throw new IllegalArgumentException("Forecast is outside your assigned zone");
        }
        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.SECURITY) {
            throw new IllegalArgumentException("Detailed forecasts are not available to citizen accounts");
        }
        return forecasts.forecast(zoneId);
    }

    @GetMapping("/api/zones/{zoneId}/stampede-likelihood")
    public com.nirikshan.dto.StampedeLikelihoodResponse stampede(@PathVariable Long zoneId) {
        var user = currentUser.get();
        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.SECURITY) throw new IllegalArgumentException("Detailed stampede signals are not available to citizen accounts");
        return forecasts.forecast(zoneId).stampedeLikelihood();
    }

    @GetMapping("/api/zones/{zoneId}/panic-propagation")
    public com.nirikshan.dto.PanicPropagationResponse propagation(@PathVariable Long zoneId) {
        var user = currentUser.get();
        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.SECURITY) throw new IllegalArgumentException("Detailed propagation signals are not available to citizen accounts");
        return forecasts.forecast(zoneId).panicPropagation();
    }

    @GetMapping("/api/venue/risk-forecast")
    public List<CitizenRiskForecastResponse> venue(@RequestParam(required = false) Long venueId) {
        Long selectedVenue = venueId;
        if (selectedVenue == null) selectedVenue = venues.findAll().stream().findFirst().map(v -> v.getId()).orElseThrow(() -> new ResourceNotFoundException("Venue", 0L));
        return forecasts.forecastVenue(selectedVenue).stream().map(forecasts::citizen).toList();
    }
}
