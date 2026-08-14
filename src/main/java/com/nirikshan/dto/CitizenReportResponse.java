package com.nirikshan.dto;

import java.time.Instant;

public record CitizenReportResponse(
        Long id,
        Long zoneId,
        String zoneName,
        String description,
        Instant timestamp,
        String status) {}
