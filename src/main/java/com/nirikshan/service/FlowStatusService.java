package com.nirikshan.service;

import com.nirikshan.dto.FlowStatusResponse;
import com.nirikshan.model.FlowBehaviorState;
import com.nirikshan.repository.ZoneRepository;
import org.springframework.stereotype.Service;

@Service
public class FlowStatusService {
    private final ZoneRepository zones;
    private final RiskForecastService forecasts;
    public FlowStatusService(ZoneRepository zones, RiskForecastService forecasts) { this.zones = zones; this.forecasts = forecasts; }

    public FlowStatusResponse get(Long zoneId) {
        var zone = zones.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("Zone", zoneId));
        var forecast = forecasts.forecast(zoneId);
        boolean sufficient = forecast.flowState() != FlowBehaviorState.INSUFFICIENT_DATA
                && "SUFFICIENT".equals(forecast.dataSufficiency());
        boolean directionAvailable = forecast.direction() != null && forecast.directionDegrees() != null
                && forecast.directionConfidence() > 0 && !forecast.stale();
        return new FlowStatusResponse(zoneId, zone.getName(), forecast.lastTelemetryAt(),
                directionAvailable ? forecast.direction() : null, directionAvailable ? forecast.directionDegrees() : null,
                directionAvailable ? forecast.directionConfidence() : 0, directionAvailable ? forecast.directionalConsistency() : 0,
                directionAvailable ? forecast.reverseMovementRatio() : 0, directionAvailable ? forecast.conflictingMovementRatio() : 0,
                sufficient ? forecast.flowState() : FlowBehaviorState.INSUFFICIENT_DATA,
                forecast.behaviorExplanation(), sufficient,
                forecast.analysisGeneratedAt(), forecast.analysisWindowStart(), forecast.analysisWindowEnd(),
                forecast.nextAnalysisAt(), forecast.analysisIntervalSeconds(), forecast.dataSufficiency(), forecast.unusualBehavior());
    }
}
