package com.nirikshan.dto;

import com.nirikshan.model.RiskLevel;
import java.time.Instant;
import java.util.List;

public record RiskEventResponse(Long id, Long zoneId, Instant timestamp, double densityScore, int peopleCount, double movementSpeed,
                                RiskLevel riskLevel, String explanation, List<HotspotRegion> hotspotRegions, boolean bottleneckDetected, String sourceClipId,
                                double densityChange, double movementSlowdown, long hotspotPersistenceSeconds, String source) {}
