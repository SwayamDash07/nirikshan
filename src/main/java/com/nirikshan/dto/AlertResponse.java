package com.nirikshan.dto;

import com.nirikshan.model.RiskLevel;
import com.nirikshan.model.RiskEventSource;
import java.time.Instant;

public record AlertResponse(Long id, Long zoneId, String zoneName, Instant timestamp, String message,
                            RiskLevel severity, boolean resolved, Instant resolvedAt, RiskEventSource source) {}
