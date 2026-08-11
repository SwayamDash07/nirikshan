package com.nirikshan.dto;

import com.nirikshan.model.RiskLevel;
import java.time.Instant;

public record RiskEventResponse(Long id, Long zoneId, Instant timestamp, double densityScore, int peopleCount, double movementSpeed,
                                RiskLevel riskLevel, String explanation, String sourceClipId) {}
