package com.nirikshan.dto;

import java.time.Instant;

public record ScenarioRunResponse(String runId, String scenarioType, Long zoneId, String status, Double speed, Instant startedAt, Instant completedAt, String message) { }
