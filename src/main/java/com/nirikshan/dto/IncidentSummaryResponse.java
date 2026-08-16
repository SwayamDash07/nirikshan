package com.nirikshan.dto;

import java.time.Instant;

public record IncidentSummaryResponse(String summary, String language, String scope, Long zoneId,
                                      String zoneName, Instant generatedAt) { }
