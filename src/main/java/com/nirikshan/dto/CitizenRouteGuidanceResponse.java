package com.nirikshan.dto;

import java.time.Instant;

public record CitizenRouteGuidanceResponse(Long venueId, Long originZoneId, String routeName,
                                           String guidance, String exitOrGate, int expectedTravelTimeSeconds,
                                           boolean routeAvailable, Instant generatedAt, String source) { }
