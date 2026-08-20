package com.nirikshan.service;

import com.nirikshan.model.RiskLevel;

public record RiskEventIngestedEvent(Long eventId, Long zoneId, RiskLevel previousRisk, Long previousEventId) {
}
