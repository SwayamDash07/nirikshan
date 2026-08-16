package com.nirikshan.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirikshan.dto.CitizenRiskForecastResponse;
import com.nirikshan.dto.RiskForecastPoint;
import com.nirikshan.dto.RiskForecastResponse;
import com.nirikshan.model.RiskEvent;
import com.nirikshan.model.RiskEventSource;
import com.nirikshan.model.FlowBehaviorState;
import com.nirikshan.model.RiskForecastState;
import com.nirikshan.model.RiskLevel;
import com.nirikshan.model.Zone;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.ZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic early-warning forecast. It deliberately has no AI dependency.
 * Density is EWMA-smoothed, then projected with a bounded least-squares slope.
 */
@Service
public class RiskForecastService {
    public static final int[] HORIZONS_SECONDS = {60, 180, 300, 600};
    public static final int MIN_READINGS = 5;
    public static final long MIN_SPAN_SECONDS = 30;
    public static final long STALE_AFTER_SECONDS = 30;
    private static final int MAX_READINGS = 50;
    private static final long HYSTERESIS_SECONDS = 20;
    private static final double EWMA_ALPHA = 0.4;
    private static final double MAX_DENSITY_SLOPE_PER_SECOND = 0.02;
    private static final double MEDIUM_DENSITY = 1.5;
    private static final double HIGH_DENSITY = 4.0;
    private static final double CRITICAL_DENSITY = 6.0;
    private static final double MAX_FORECAST_DENSITY = 9.0;

    private final RiskEventRepository events;
    private final ZoneRepository zones;
    private final ObjectMapper objectMapper;
    private final long analysisIntervalSeconds;
    private final int flowMinSamples;
    private final long flowMinSpanSeconds;
    private final RiskIntelligenceService intelligence;
    private final Map<Long, CachedForecast> cache = new ConcurrentHashMap<>();

    @Autowired
    public RiskForecastService(RiskEventRepository events, ZoneRepository zones, ObjectMapper objectMapper,
                               @Value("${nirikshan.analysis.interval-seconds:30}") long analysisIntervalSeconds,
                               @Value("${nirikshan.analysis.flow-min-samples:3}") int flowMinSamples,
                               @Value("${nirikshan.analysis.flow-min-span-seconds:10}") long flowMinSpanSeconds,
                               RiskIntelligenceService intelligence) {
        this.events = events;
        this.zones = zones;
        this.objectMapper = objectMapper;
        this.analysisIntervalSeconds = Math.max(1, analysisIntervalSeconds);
        this.flowMinSamples = Math.max(1, flowMinSamples);
        this.flowMinSpanSeconds = Math.max(0, flowMinSpanSeconds);
        this.intelligence = intelligence;
    }

    /** Compatibility constructor for deterministic calculation tests and small local tools. */
    public RiskForecastService(RiskEventRepository events, ZoneRepository zones) {
        this(events, zones, new ObjectMapper(), 30, 3, 10, new RiskIntelligenceService(events, zones));
    }

    @Transactional(readOnly = true)
    public RiskForecastResponse forecast(Long zoneId) {
        Zone zone = zones.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("Zone", zoneId));
        List<RiskEvent> recent = validReadings(events.findByZoneIdOrderByTimestampDesc(zoneId, PageRequest.of(0, 100)), null);
        if (recent.isEmpty()) {
            cache.remove(zoneId);
            return offline(zone, Instant.now());
        }
        RiskEvent latest = recent.stream().filter(event -> event.getTimestamp() != null)
                .max(Comparator.comparing(RiskEvent::getTimestamp)).orElse(null);
        // A completed simulation is retained for audit/history, but must not be
        // treated as current telemetry after restore has reset the zone.
        if (latest != null && latest.getSource() == RiskEventSource.SIMULATION
                && (zone.getLastUpdated() == null || !zone.getLastUpdated().equals(latest.getTimestamp()))) {
            cache.remove(zoneId);
            List<RiskEvent> live = validReadings(recent, RiskEventSource.LIVE);
            return live.isEmpty() ? offline(zone, Instant.now()) : forecastFor(zone, live, RiskEventSource.LIVE);
        }
        return forecastFor(zone, recent, null);
    }

    @Transactional(readOnly = true)
    public RiskForecastResponse forecastLive(Long zoneId) {
        Zone zone = zones.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("Zone", zoneId));
        List<RiskEvent> recentLive = validReadings(events.findByZoneIdOrderByTimestampDesc(zoneId, PageRequest.of(0, 100)), RiskEventSource.LIVE);
        if (recentLive.isEmpty()) {
            cache.remove(zoneId);
            return offline(zone, Instant.now());
        }
        return forecastFor(zone, recentLive, RiskEventSource.LIVE);
    }

    @Transactional(readOnly = true)
    public RiskForecastResponse offline(Long zoneId) {
        Zone zone = zones.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("Zone", zoneId));
        return offline(zone, Instant.now());
    }

    private RiskForecastResponse offline(Zone zone, Instant generatedAt) {
        cache.remove(zone.getId());
        RiskForecastResponse base = response(zone, generatedAt, zone.getLastUpdated(), RiskLevel.LOW, RiskLevel.LOW,
                zone.getCurrentDensity(), zone.getCurrentDensity(), 0, 0, 0, 0, 0,
                false, 0, RiskForecastState.INSUFFICIENT_DATA,
                "No live telemetry is available; the simulation has ended and this zone is offline.",
                RiskEventSource.LIVE, true, List.of());
        return withSnapshotMetadata(withFlowUnavailable(base, "No valid movement data is available because this zone is offline."), List.of(), generatedAt);
    }

    private RiskForecastResponse forecastFor(Zone zone, List<RiskEvent> readings, RiskEventSource sourceFilter) {
        Instant now = Instant.now();
        ForecastKey key = ForecastKey.from(zone.getId(), readings, sourceFilter);
        CachedForecast existing = cache.get(zone.getId());
        RiskForecastResponse calculated = calculate(zone, readings, now);
        RiskForecastState heldState = calculated.state();
        double confidence = calculated.confidence();
        boolean sameSourceAnalysis = existing != null && Objects.equals(existing.key().sourceFilter(), sourceFilter);
        if (sameSourceAnalysis && !calculated.stale() && calculated.state() != RiskForecastState.INSUFFICIENT_DATA) {
            heldState = stabilizeState(readings, calculated.state(), existing.heldState());
            RiskLevel heldRisk = heldProjectedRisk(calculated.projectedRisk(), heldState);
            confidence = stabilizeConfidence(calculated.confidence(), existing.confidence());
            calculated = withAnalysis(calculated, heldRisk, heldState, confidence,
                    explanationFor(calculated, heldState, heldRisk, confidence));
        }
        calculated = withFlowSnapshot(calculated, readings, now);
        calculated = withIntelligence(calculated, readings, zone);
        if (existing == null || !sameSourceAnalysis || shouldCommit(existing.forecast(), calculated, now)) {
            RiskForecastResponse committed = withSnapshotMetadata(calculated, readings, now);
            cache.put(zone.getId(), new CachedForecast(key, committed, heldState, committed.confidence()));
            return committed;
        }
        // Keep the analytical values frozen, but carry the newest telemetry
        // timestamp so the UI can show live age without replacing the snapshot.
        return withLiveTelemetry(existing.forecast(), calculated.lastTelemetryAt(), calculated.stale());
    }

    private static boolean isStale(Instant lastTelemetry, Instant now) {
        return lastTelemetry == null || Math.max(0, Duration.between(lastTelemetry, now).getSeconds()) > STALE_AFTER_SECONDS;
    }

    private static RiskForecastResponse staleVersion(RiskForecastResponse forecast, Instant now) {
        return withFlowUnavailable(withAnalysis(forecast, forecast.projectedRisk(), RiskForecastState.INSUFFICIENT_DATA,
                0, "Forecast based on last telemetry; telemetry is stale and is no longer presented as live."),
                "Telemetry is stale; no valid movement data is available for the current analysis window.");
    }

    private static RiskForecastResponse withGeneratedAt(RiskForecastResponse forecast, Instant generatedAt) {
        return new RiskForecastResponse(forecast.zoneId(), forecast.zoneName(), generatedAt, forecast.lastTelemetryAt(),
                forecast.currentRisk(), forecast.projectedRisk(), forecast.forecastHorizonSeconds(), forecast.estimatedSecondsToProjectedRisk(),
                forecast.currentDensity(), forecast.projectedDensity(), forecast.densityTrendPerMinute(), forecast.currentMovementSpeed(),
                forecast.movementSlowdown(), forecast.movementSlowdownTrendPerMinute(), forecast.hotspotPersistenceSeconds(),
                forecast.bottleneckDetected(), forecast.confidence(), forecast.state(), forecast.explanation(), forecast.source(),
                forecast.stale(), forecast.projections(), forecast.dominantDirection(), forecast.directionDegrees(), forecast.directionConfidence(),
                forecast.directionalConsistency(), forecast.reverseMovementRatio(), forecast.conflictingMovementRatio(), forecast.behaviorState(), forecast.behaviorExplanation(),
                forecast.analysisGeneratedAt(), forecast.analysisWindowStart(), forecast.analysisWindowEnd(), forecast.nextAnalysisAt(),
                forecast.analysisIntervalSeconds(), forecast.dataSufficiency(), forecast.flowState(), forecast.direction(), forecast.analysisPeopleCount(), forecast.analysisHotspotRegions());
    }

    private static List<RiskEvent> validReadings(List<RiskEvent> input, RiskEventSource sourceFilter) {
        Map<Instant, RiskEvent> byTimestamp = new LinkedHashMap<>();
        input.stream()
                .filter(event -> event != null && event.getTimestamp() != null)
                .filter(event -> sourceFilter == null ? event.getSource() == RiskEventSource.LIVE || event.getSource() == RiskEventSource.SIMULATION : event.getSource() == sourceFilter)
                .filter(RiskForecastService::validReading)
                .sorted(Comparator.comparing(RiskEvent::getTimestamp).thenComparing(event -> event.getId() == null ? 0L : event.getId()))
                .forEach(event -> byTimestamp.put(event.getTimestamp(), event));
        List<RiskEvent> unique = new ArrayList<>(byTimestamp.values());
        return unique.size() <= MAX_READINGS ? unique : unique.subList(unique.size() - MAX_READINGS, unique.size());
    }

    private boolean shouldCommit(RiskForecastResponse previous, RiskForecastResponse candidate, Instant now) {
        if (previous.analysisGeneratedAt() == null) return true;
        if (Duration.between(previous.analysisGeneratedAt(), now).getSeconds() >= analysisIntervalSeconds) return true;
        if (previous.analysisWindowEnd() != null && candidate.lastTelemetryAt() != null
                && Duration.between(previous.analysisWindowEnd(), candidate.lastTelemetryAt()).getSeconds() >= analysisIntervalSeconds) return true;
        if (previous.state() == RiskForecastState.INSUFFICIENT_DATA
                && candidate.state() != RiskForecastState.INSUFFICIENT_DATA) return true;
        if (!Objects.equals(previous.dataSufficiency(), candidate.dataSufficiency())) return true;
        if (previous.stale() != candidate.stale()) return true;
        if (previous.bottleneckDetected() != candidate.bottleneckDetected()) return true;
        if (candidate.state() != previous.state()
                && (candidate.state() == RiskForecastState.CRUSH_RISK
                || candidate.state() == RiskForecastState.SURGE_RISK
                || candidate.state() == RiskForecastState.RECOVERING)) return true;
        return false;
    }

    private RiskForecastResponse withSnapshotMetadata(RiskForecastResponse forecast, List<RiskEvent> readings, Instant generatedAt) {
        Instant windowStart = readings.isEmpty() ? null : readings.get(0).getTimestamp();
        Instant windowEnd = readings.isEmpty() ? forecast.lastTelemetryAt() : readings.get(readings.size() - 1).getTimestamp();
        return new RiskForecastResponse(forecast.zoneId(), forecast.zoneName(), generatedAt, forecast.lastTelemetryAt(),
                forecast.currentRisk(), forecast.projectedRisk(), forecast.forecastHorizonSeconds(), forecast.estimatedSecondsToProjectedRisk(),
                forecast.currentDensity(), forecast.projectedDensity(), forecast.densityTrendPerMinute(), forecast.currentMovementSpeed(),
                forecast.movementSlowdown(), forecast.movementSlowdownTrendPerMinute(), forecast.hotspotPersistenceSeconds(),
                forecast.bottleneckDetected(), forecast.confidence(), forecast.state(), forecast.explanation(), forecast.source(), forecast.stale(),
                forecast.projections(), forecast.dominantDirection(), forecast.directionDegrees(), forecast.directionConfidence(),
                forecast.directionalConsistency(), forecast.reverseMovementRatio(), forecast.conflictingMovementRatio(), forecast.behaviorState(),
                forecast.behaviorExplanation(), generatedAt, windowStart, windowEnd, generatedAt.plusSeconds(analysisIntervalSeconds),
                analysisIntervalSeconds, forecast.dataSufficiency(), forecast.flowState(), forecast.direction(), forecast.analysisPeopleCount(), forecast.analysisHotspotRegions(),
                forecast.stampedeLikelihood(), forecast.panicPropagation(), forecast.unusualBehavior());
    }

    private static RiskForecastResponse withLiveTelemetry(RiskForecastResponse snapshot, Instant lastTelemetryAt, boolean stale) {
        return new RiskForecastResponse(snapshot.zoneId(), snapshot.zoneName(), snapshot.generatedAt(), lastTelemetryAt,
                snapshot.currentRisk(), snapshot.projectedRisk(), snapshot.forecastHorizonSeconds(), snapshot.estimatedSecondsToProjectedRisk(),
                snapshot.currentDensity(), snapshot.projectedDensity(), snapshot.densityTrendPerMinute(), snapshot.currentMovementSpeed(),
                snapshot.movementSlowdown(), snapshot.movementSlowdownTrendPerMinute(), snapshot.hotspotPersistenceSeconds(),
                snapshot.bottleneckDetected(), snapshot.confidence(), snapshot.state(), snapshot.explanation(), snapshot.source(), stale,
                snapshot.projections(), snapshot.dominantDirection(), snapshot.directionDegrees(), snapshot.directionConfidence(),
                snapshot.directionalConsistency(), snapshot.reverseMovementRatio(), snapshot.conflictingMovementRatio(), snapshot.behaviorState(),
                snapshot.behaviorExplanation(), snapshot.analysisGeneratedAt(), snapshot.analysisWindowStart(), snapshot.analysisWindowEnd(),
                snapshot.nextAnalysisAt(), snapshot.analysisIntervalSeconds(), snapshot.dataSufficiency(), snapshot.flowState(), snapshot.direction(),
                snapshot.analysisPeopleCount(), snapshot.analysisHotspotRegions(), snapshot.stampedeLikelihood(), snapshot.panicPropagation(), snapshot.unusualBehavior());
    }

    private RiskForecastResponse withIntelligence(RiskForecastResponse forecast, List<RiskEvent> readings, Zone zone) {
        var stampede = intelligence.stampede(readings, forecast.projectedDensity(), forecast.densityTrendPerMinute(),
                forecast.movementSlowdown(), forecast.hotspotPersistenceSeconds(), forecast.bottleneckDetected(),
                forecast.source(), zone.getId());
        var unusual = intelligence.unusual(readings);
        var propagation = intelligence.propagation(zone.getId());
        return new RiskForecastResponse(forecast.zoneId(), forecast.zoneName(), forecast.generatedAt(), forecast.lastTelemetryAt(),
                forecast.currentRisk(), forecast.projectedRisk(), forecast.forecastHorizonSeconds(), forecast.estimatedSecondsToProjectedRisk(),
                forecast.currentDensity(), forecast.projectedDensity(), forecast.densityTrendPerMinute(), forecast.currentMovementSpeed(),
                forecast.movementSlowdown(), forecast.movementSlowdownTrendPerMinute(), forecast.hotspotPersistenceSeconds(),
                forecast.bottleneckDetected(), forecast.confidence(), forecast.state(), forecast.explanation(), forecast.source(), forecast.stale(),
                forecast.projections(), forecast.dominantDirection(), forecast.directionDegrees(), forecast.directionConfidence(),
                forecast.directionalConsistency(), forecast.reverseMovementRatio(), forecast.conflictingMovementRatio(), forecast.behaviorState(),
                forecast.behaviorExplanation(), forecast.analysisGeneratedAt(), forecast.analysisWindowStart(), forecast.analysisWindowEnd(),
                forecast.nextAnalysisAt(), forecast.analysisIntervalSeconds(), forecast.dataSufficiency(), forecast.flowState(), forecast.direction(),
                forecast.analysisPeopleCount(), forecast.analysisHotspotRegions(), stampede, propagation, unusual);
    }

    private static RiskForecastResponse withFlowUnavailable(RiskForecastResponse forecast, String explanation) {
        return new RiskForecastResponse(forecast.zoneId(), forecast.zoneName(), forecast.generatedAt(), forecast.lastTelemetryAt(),
                forecast.currentRisk(), forecast.projectedRisk(), forecast.forecastHorizonSeconds(), forecast.estimatedSecondsToProjectedRisk(),
                forecast.currentDensity(), forecast.projectedDensity(), forecast.densityTrendPerMinute(), forecast.currentMovementSpeed(),
                forecast.movementSlowdown(), forecast.movementSlowdownTrendPerMinute(), forecast.hotspotPersistenceSeconds(),
                forecast.bottleneckDetected(), forecast.confidence(), forecast.state(), forecast.explanation(), forecast.source(), forecast.stale(),
                forecast.projections(), "Unknown", null, 0, 0, 0, 0, FlowBehaviorState.INSUFFICIENT_DATA, explanation,
                forecast.analysisGeneratedAt(), forecast.analysisWindowStart(), forecast.analysisWindowEnd(), forecast.nextAnalysisAt(),
                forecast.analysisIntervalSeconds(), "INSUFFICIENT_DATA", FlowBehaviorState.INSUFFICIENT_DATA, null, forecast.analysisPeopleCount(), List.of());
    }

    private static RiskForecastResponse withPartialFlow(RiskForecastResponse forecast, String direction, double degrees,
                                                         double directionConfidence, double directionalConsistency,
                                                         double reverseMovementRatio, double conflictingMovementRatio,
                                                         String explanation, int analysisPeopleCount, List<com.nirikshan.dto.HotspotRegion> hotspots) {
        return new RiskForecastResponse(forecast.zoneId(), forecast.zoneName(), forecast.generatedAt(), forecast.lastTelemetryAt(),
                forecast.currentRisk(), forecast.projectedRisk(), forecast.forecastHorizonSeconds(), forecast.estimatedSecondsToProjectedRisk(),
                forecast.currentDensity(), forecast.projectedDensity(), forecast.densityTrendPerMinute(), forecast.currentMovementSpeed(),
                forecast.movementSlowdown(), forecast.movementSlowdownTrendPerMinute(), forecast.hotspotPersistenceSeconds(),
                forecast.bottleneckDetected(), forecast.confidence(), forecast.state(), forecast.explanation(), forecast.source(), forecast.stale(),
                forecast.projections(), direction, degrees, directionConfidence, directionalConsistency,
                reverseMovementRatio, conflictingMovementRatio, FlowBehaviorState.INSUFFICIENT_DATA, explanation,
                forecast.analysisGeneratedAt(), forecast.analysisWindowStart(), forecast.analysisWindowEnd(), forecast.nextAnalysisAt(),
                forecast.analysisIntervalSeconds(), "PARTIAL", FlowBehaviorState.INSUFFICIENT_DATA, direction, analysisPeopleCount, hotspots);
    }

    private RiskForecastResponse withFlowSnapshot(RiskForecastResponse forecast, List<RiskEvent> readings, Instant now) {
        List<RiskEvent> flowReadings = readings.stream().filter(RiskForecastService::validFlowReading).toList();
        if (forecast.stale() || flowReadings.size() < flowMinSamples) {
            String reason = forecast.stale()
                    ? "Telemetry is stale; no valid movement data is available for the current analysis window."
                    : "Too few valid movement trajectories were observed across the analysis window.";
            return withFlowUnavailable(withStableFacts(forecast, readings), reason);
        }
        Instant start = flowReadings.get(0).getTimestamp();
        Instant end = flowReadings.get(flowReadings.size() - 1).getTimestamp();
        if (Duration.between(start, end).getSeconds() < flowMinSpanSeconds) {
            return withFlowUnavailable(withStableFacts(forecast, readings), "The valid movement data does not span the minimum analysis time window.");
        }
        double x = 0, y = 0, confidence = 0, consistency = 0, reverse = 0, conflicting = 0;
        for (RiskEvent event : flowReadings) {
            double weight = Math.max(0.05, event.getDirectionConfidence());
            double radians = Math.toRadians(event.getDirectionDegrees());
            x += Math.cos(radians) * weight;
            y += Math.sin(radians) * weight;
            confidence += event.getDirectionConfidence();
            consistency += event.getDirectionalConsistency();
            reverse += event.getReverseMovementRatio();
            conflicting += event.getConflictingMovementRatio();
        }
        double averageConsistency = consistency / flowReadings.size();
        if (averageConsistency < .35 || Math.hypot(x, y) <= 0.01) {
            return withFlowUnavailable(withStableFacts(forecast, readings), "Directional consistency is too low to report a reliable dominant direction.");
        }
        double degrees = Math.toDegrees(Math.atan2(y, x));
        if (degrees < 0) degrees += 360;
        String direction = directionName(degrees);
        double averageConfidence = Math.min(1, Math.max(0, confidence / flowReadings.size() * averageConsistency));
        RiskEvent stableBehaviorReading = flowReadings.stream()
                .filter(event -> event.getBehaviorState() != null && event.getBehaviorState() != FlowBehaviorState.INSUFFICIENT_DATA)
                .reduce((first, second) -> second)
                .orElse(null);
        FlowBehaviorState behavior = stableBehaviorReading == null
                ? FlowBehaviorState.INSUFFICIENT_DATA : stableBehaviorReading.getBehaviorState();
        if (behavior == FlowBehaviorState.INSUFFICIENT_DATA) {
            return withPartialFlow(withStableFacts(forecast, readings), direction, degrees, averageConfidence,
                    averageConsistency, reverse / flowReadings.size(), conflicting / flowReadings.size(),
                    "Movement direction is available, but behavior is still stabilizing.",
                    flowReadings.get(flowReadings.size() - 1).getPeopleCount(), stableHotspots(readings));
        }
        String explanation = stableBehaviorReading.getBehaviorExplanation();
        if (explanation == null || explanation.isBlank()) explanation = "Valid tracked-person movement is consistent across the analysis window.";
        return new RiskForecastResponse(forecast.zoneId(), forecast.zoneName(), forecast.generatedAt(), forecast.lastTelemetryAt(),
                forecast.currentRisk(), forecast.projectedRisk(), forecast.forecastHorizonSeconds(), forecast.estimatedSecondsToProjectedRisk(),
                forecast.currentDensity(), forecast.projectedDensity(), forecast.densityTrendPerMinute(), forecast.currentMovementSpeed(),
                forecast.movementSlowdown(), forecast.movementSlowdownTrendPerMinute(), forecast.hotspotPersistenceSeconds(),
                forecast.bottleneckDetected(), forecast.confidence(), forecast.state(), forecast.explanation(), forecast.source(), forecast.stale(),
                forecast.projections(), direction, degrees, averageConfidence, averageConsistency,
                reverse / flowReadings.size(), conflicting / flowReadings.size(), behavior, explanation,
                forecast.analysisGeneratedAt(), forecast.analysisWindowStart(), forecast.analysisWindowEnd(), forecast.nextAnalysisAt(),
                forecast.analysisIntervalSeconds(), "SUFFICIENT", behavior, direction, flowReadings.get(flowReadings.size() - 1).getPeopleCount(), stableHotspots(readings));
    }

    private RiskForecastResponse withStableFacts(RiskForecastResponse forecast, List<RiskEvent> readings) {
        if (readings.isEmpty()) return forecast;
        RiskEvent latest = readings.get(readings.size() - 1);
        return new RiskForecastResponse(forecast.zoneId(), forecast.zoneName(), forecast.generatedAt(), forecast.lastTelemetryAt(),
                forecast.currentRisk(), forecast.projectedRisk(), forecast.forecastHorizonSeconds(), forecast.estimatedSecondsToProjectedRisk(),
                forecast.currentDensity(), forecast.projectedDensity(), forecast.densityTrendPerMinute(), forecast.currentMovementSpeed(),
                forecast.movementSlowdown(), forecast.movementSlowdownTrendPerMinute(), forecast.hotspotPersistenceSeconds(),
                forecast.bottleneckDetected(), forecast.confidence(), forecast.state(), forecast.explanation(), forecast.source(), forecast.stale(),
                forecast.projections(), forecast.dominantDirection(), forecast.directionDegrees(), forecast.directionConfidence(),
                forecast.directionalConsistency(), forecast.reverseMovementRatio(), forecast.conflictingMovementRatio(), forecast.behaviorState(),
                forecast.behaviorExplanation(), forecast.analysisGeneratedAt(), forecast.analysisWindowStart(), forecast.analysisWindowEnd(),
                forecast.nextAnalysisAt(), forecast.analysisIntervalSeconds(), forecast.dataSufficiency(), forecast.flowState(), forecast.direction(),
                latest.getPeopleCount(), stableHotspots(readings));
    }

    private static boolean validFlowReading(RiskEvent event) {
        return event.getDirectionDegrees() != null
                && Double.isFinite(event.getDirectionDegrees())
                && event.getDominantDirection() != null
                && !event.getDominantDirection().isBlank()
                && event.getDirectionConfidence() > 0;
    }

    private static String directionName(double degrees) {
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) (((degrees % 360) + 22.5) / 45) % 8];
    }

    private List<com.nirikshan.dto.HotspotRegion> stableHotspots(List<RiskEvent> readings) {
        RiskEvent anchor = readings.stream()
                .max(Comparator.comparingLong(RiskEvent::getHotspotPersistenceSeconds).thenComparing(RiskEvent::getTimestamp))
                .orElse(null);
        if (anchor == null || anchor.getHotspotRegions() == null || anchor.getHotspotRegions().isBlank()) return List.of();
        try {
            return objectMapper.readValue(anchor.getHotspotRegions(), new TypeReference<>() { });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static boolean validReading(RiskEvent event) {
        return event.getRiskLevel() != null
                && event.getPeopleCount() >= 0
                && Double.isFinite(event.getDensityScore()) && event.getDensityScore() >= 0
                && Double.isFinite(event.getMovementSpeed()) && event.getMovementSpeed() >= 0
                && Double.isFinite(event.getMovementSlowdown()) && event.getMovementSlowdown() >= 0
                && event.getHotspotPersistenceSeconds() >= 0;
    }

    @Transactional(readOnly = true)
    public List<RiskForecastResponse> forecastVenue(Long venueId) {
        if (!zones.existsByVenueId(venueId)) throw new ResourceNotFoundException("Venue", venueId);
        return zones.findByVenueId(venueId).stream().map(zone -> forecast(zone.getId())).toList();
    }

    public CitizenRiskForecastResponse citizen(RiskForecastResponse forecast) {
        String message;
        if (forecast.stale()) {
            message = "Safety data is stale; reconnect to refresh.";
        } else if (forecast.stampedeLikelihood() != null && "HIGH".equals(forecast.stampedeLikelihood().level())) {
            message = "Crowd movement may become unsafe near " + forecast.zoneName() + ". Move away from the congested area and follow staff directions.";
        } else if (forecast.state() == RiskForecastState.RECOVERING || forecast.projectedRisk() == RiskLevel.LOW) {
            message = "Conditions are settling back toward normal.";
        } else if (forecast.projectedRisk().ordinal() >= RiskLevel.HIGH.ordinal()) {
            message = "Crowding may worsen shortly near " + forecast.zoneName() + ". Please use the recommended alternate route.";
        } else if (forecast.state() == RiskForecastState.RISING) {
            message = "Crowding is increasing near " + forecast.zoneName() + ". Conditions may worsen shortly.";
        } else {
            message = "Conditions near " + forecast.zoneName() + " are currently stable.";
        }
        return new CitizenRiskForecastResponse(forecast.zoneId(), forecast.zoneName(), forecast.generatedAt(),
                forecast.lastTelemetryAt(), forecast.currentRisk(), forecast.projectedRisk(), forecast.state(),
                message, forecast.stale(), forecast.source(), forecast.stampedeLikelihood() == null ? "INSUFFICIENT_DATA" : forecast.stampedeLikelihood().level());
    }

    public static RiskForecastResponse calculate(Zone zone, List<RiskEvent> input, Instant generatedAt) {
        List<RiskEvent> readings = validReadings(input, null);
        RiskEvent latest = readings.isEmpty() ? null : readings.get(readings.size() - 1);
        Instant lastTelemetry = latest == null ? zone.getLastUpdated() : latest.getTimestamp();
        RiskLevel currentRisk = latest == null ? zone.getCurrentRiskLevel() : latest.getRiskLevel();
        RiskEventSource source = latest == null || latest.getSource() == null ? RiskEventSource.LIVE : latest.getSource();
        double currentDensity = latest == null ? zone.getCurrentDensity() : latest.getDensityScore();
        double currentSpeed = latest == null ? 0 : latest.getMovementSpeed();
        boolean stale = lastTelemetry == null || Math.max(0, Duration.between(lastTelemetry, generatedAt).getSeconds()) > STALE_AFTER_SECONDS;
        long span = readings.size() < 2 ? 0 : Math.max(0, Duration.between(readings.get(0).getTimestamp(), lastTelemetry).getSeconds());
        boolean sufficient = readings.size() >= MIN_READINGS && span >= MIN_SPAN_SECONDS && !stale;

        if (!sufficient) {
            String explanation = stale
                    ? "Telemetry is stale; the last reading is not recent enough to support an early warning."
                    : "Not enough telemetry for a forecast; need at least 5 valid readings spanning 30 seconds.";
            return response(zone, generatedAt, lastTelemetry, currentRisk, currentRisk, currentDensity, currentDensity,
                    0, currentSpeed, 0, 0, latest == null ? 0 : latest.getHotspotPersistenceSeconds(),
                    zone.isBottleneckDetected(), 0, RiskForecastState.INSUFFICIENT_DATA, explanation, source, stale, List.of(),
                    null, "Unknown", null, 0, 0, 0, 0, FlowBehaviorState.INSUFFICIENT_DATA, explanation);
        }

        double smoothedDensity = ewma(readings, true);
        double smoothedSpeed = ewma(readings, false);
        double densitySlope = boundedSlope(readings, true);
        double slowdown = averageSlowdown(readings);
        double slowdownTrend = boundedSlope(readings, false, true);
        double projectedDensity = project(smoothedDensity, densitySlope, 600);
        List<RiskForecastPoint> projections = new ArrayList<>();
        for (int horizon : HORIZONS_SECONDS) projections.add(new RiskForecastPoint(horizon, project(smoothedDensity, densitySlope, horizon)));
        RiskLevel projectedRisk = projectedRisk(projectedDensity, densitySlope, slowdown, zone.isBottleneckDetected());
        long hotspotPersistence = readings.stream().mapToLong(RiskEvent::getHotspotPersistenceSeconds).max().orElse(0);
        RiskForecastState state = rawState(currentRisk, projectedRisk, densitySlope, zone.isBottleneckDetected(), slowdown);
        double confidence = confidence(readings, generatedAt, densitySlope, projectedRisk, zone.isBottleneckDetected(), slowdown);
        Long secondsToThreshold = secondsToThreshold(currentRisk, currentDensity, densitySlope, projectedRisk);
        String explanation = explanation(state, projectedRisk, densitySlope, slowdown, hotspotPersistence, secondsToThreshold, confidence);
        return response(zone, generatedAt, lastTelemetry, currentRisk, projectedRisk, smoothedDensity, projectedDensity,
                densitySlope * 60, smoothedSpeed, slowdown, slowdownTrend * 60, hotspotPersistence,
                zone.isBottleneckDetected(), confidence, state, explanation, source, false, projections, secondsToThreshold,
                latest.getDominantDirection(), latest.getDirectionDegrees(), latest.getDirectionConfidence(), latest.getDirectionalConsistency(),
                latest.getReverseMovementRatio(), latest.getConflictingMovementRatio(), latest.getBehaviorState(), latest.getBehaviorExplanation());
    }

    private static RiskForecastResponse response(Zone zone, Instant generatedAt, Instant lastTelemetry, RiskLevel currentRisk,
                                                 RiskLevel projectedRisk, double currentDensity, double projectedDensity,
                                                 double densityTrendPerMinute, double currentSpeed, double slowdown,
                                                 double slowdownTrendPerMinute, long hotspotPersistence, boolean bottleneck,
                                                 double confidence, RiskForecastState state, String explanation,
                                                 RiskEventSource source, boolean stale, List<RiskForecastPoint> projections) {
        return response(zone, generatedAt, lastTelemetry, currentRisk, projectedRisk, currentDensity, projectedDensity,
                densityTrendPerMinute, currentSpeed, slowdown, slowdownTrendPerMinute, hotspotPersistence, bottleneck,
                confidence, state, explanation, source, stale, projections, null,
                "Unknown", null, 0, 0, 0, 0, FlowBehaviorState.INSUFFICIENT_DATA, explanation);
    }

    private static RiskForecastResponse response(Zone zone, Instant generatedAt, Instant lastTelemetry, RiskLevel currentRisk,
                                                 RiskLevel projectedRisk, double currentDensity, double projectedDensity,
                                                 double densityTrendPerMinute, double currentSpeed, double slowdown,
                                                 double slowdownTrendPerMinute, long hotspotPersistence, boolean bottleneck,
                                                 double confidence, RiskForecastState state, String explanation,
                                                 RiskEventSource source, boolean stale, List<RiskForecastPoint> projections,
                                                 Long secondsToThreshold, String dominantDirection, Double directionDegrees,
                                                 double directionConfidence, double directionalConsistency, double reverseMovementRatio,
                                                 double conflictingMovementRatio, FlowBehaviorState behaviorState, String behaviorExplanation) {
        return new RiskForecastResponse(zone.getId(), zone.getName(), generatedAt, lastTelemetry, currentRisk,
                projectedRisk, 600, secondsToThreshold, round(currentDensity), round(projectedDensity),
                round(densityTrendPerMinute), round(currentSpeed), round(slowdown), round(slowdownTrendPerMinute),
                hotspotPersistence, bottleneck, round(confidence), state, explanation, source, stale, projections,
                dominantDirection, directionDegrees, round(directionConfidence), round(directionalConsistency),
                round(reverseMovementRatio), round(conflictingMovementRatio), behaviorState, behaviorExplanation,
                generatedAt, null, lastTelemetry, generatedAt.plusSeconds(30), 30,
                behaviorState == FlowBehaviorState.INSUFFICIENT_DATA ? "INSUFFICIENT_DATA" : "SUFFICIENT",
                behaviorState, dominantDirection, 0, List.of());
    }

    private static double ewma(List<RiskEvent> readings, boolean density) {
        double value = density ? readings.get(0).getDensityScore() : readings.get(0).getMovementSpeed();
        for (int i = 1; i < readings.size(); i++) {
            double next = density ? readings.get(i).getDensityScore() : readings.get(i).getMovementSpeed();
            value = EWMA_ALPHA * next + (1 - EWMA_ALPHA) * value;
        }
        return value;
    }

    private static double averageSlowdown(List<RiskEvent> readings) {
        return readings.stream().mapToDouble(RiskEvent::getMovementSlowdown).average().orElse(0);
    }

    private static double boundedSlope(List<RiskEvent> readings, boolean density) { return boundedSlope(readings, density, false); }

    private static double boundedSlope(List<RiskEvent> readings, boolean density, boolean slowdown) {
        double meanTime = readings.stream().mapToDouble(event -> event.getTimestamp().toEpochMilli() / 1000.0).average().orElse(0);
        double meanValue = readings.stream().mapToDouble(event -> density ? event.getDensityScore() : slowdown ? event.getMovementSlowdown() : event.getMovementSpeed()).average().orElse(0);
        double numerator = 0;
        double denominator = 0;
        for (RiskEvent event : readings) {
            double time = event.getTimestamp().toEpochMilli() / 1000.0 - meanTime;
            double value = density ? event.getDensityScore() : slowdown ? event.getMovementSlowdown() : event.getMovementSpeed();
            numerator += time * (value - meanValue);
            denominator += time * time;
        }
        double slope = denominator <= 0 ? 0 : numerator / denominator;
        return Math.max(-MAX_DENSITY_SLOPE_PER_SECOND, Math.min(MAX_DENSITY_SLOPE_PER_SECOND, slope));
    }

    private static double project(double current, double slope, int seconds) {
        return round(Math.max(0, Math.min(MAX_FORECAST_DENSITY, current + slope * seconds)));
    }

    private static RiskLevel projectedRisk(double density, double slope, double slowdown, boolean bottleneck) {
        RiskLevel densityRisk = density >= CRITICAL_DENSITY ? RiskLevel.CRITICAL : density >= HIGH_DENSITY ? RiskLevel.HIGH : density >= MEDIUM_DENSITY ? RiskLevel.MEDIUM : RiskLevel.LOW;
        double trendSignal = clamp((slope * 60) / 1.2);
        double score = clamp(0.55 * clamp(density / CRITICAL_DENSITY) + 0.25 * trendSignal + 0.20 * clamp(slowdown / 0.60));
        RiskLevel scoreRisk = score >= .85 ? RiskLevel.CRITICAL : score >= .65 ? RiskLevel.HIGH : score >= .20 ? RiskLevel.MEDIUM : RiskLevel.LOW;
        RiskLevel result = max(densityRisk, scoreRisk);
        if (bottleneck && slowdown >= .30 && result.ordinal() < RiskLevel.HIGH.ordinal()) result = RiskLevel.HIGH;
        return result;
    }

    private static RiskForecastState rawState(RiskLevel current, RiskLevel projected, double slope, boolean bottleneck, double slowdown) {
        if (slope < -0.002 && projected.ordinal() <= current.ordinal()) return RiskForecastState.RECOVERING;
        if (projected == RiskLevel.CRITICAL && slope > 0.002 && (slowdown >= .20 || bottleneck)) return RiskForecastState.CRUSH_RISK;
        if (projected == RiskLevel.HIGH) return RiskForecastState.SURGE_RISK;
        if (slope > 0.002) return RiskForecastState.RISING;
        return RiskForecastState.STABLE;
    }

    private static Long secondsToThreshold(RiskLevel current, double density, double slope, RiskLevel projected) {
        RiskLevel next = current == RiskLevel.LOW ? RiskLevel.MEDIUM : current == RiskLevel.MEDIUM ? RiskLevel.HIGH : current == RiskLevel.HIGH ? RiskLevel.CRITICAL : null;
        if (next == null || projected.ordinal() < next.ordinal()) return null;
        double threshold = next == RiskLevel.MEDIUM ? MEDIUM_DENSITY : next == RiskLevel.HIGH ? HIGH_DENSITY : CRITICAL_DENSITY;
        if (density >= threshold) return 0L;
        if (slope <= 0) return null;
        return Math.min(600L, Math.max(0L, Math.round((threshold - density) / slope)));
    }

    private static double confidence(List<RiskEvent> readings, Instant generatedAt, double slope,
                                     RiskLevel projectedRisk, boolean bottleneck, double slowdown) {
        double sample = Math.min(1, readings.size() / 10.0);
        long span = Math.max(0, Duration.between(readings.get(0).getTimestamp(), readings.get(readings.size() - 1).getTimestamp()).getSeconds());
        double history = Math.min(1, span / 120.0);
        long age = Math.max(0, Duration.between(readings.get(readings.size() - 1).getTimestamp(), generatedAt).getSeconds());
        double recency = age <= 5 ? 1 : age <= 15 ? .85 : age <= STALE_AFTER_SECONDS ? .65 : .25;
        double fit = trendFit(readings, true);
        long agreeing = readings.stream().filter(event -> eventRisk(event) == projectedRisk || event.getRiskLevel().ordinal() >= projectedRisk.ordinal()).count();
        double riskAgreement = agreeing / (double) readings.size();
        boolean movementAgreement = slowdown >= .20 || readings.stream().allMatch(event -> event.getMovementSpeed() > 0);
        double signalAgreement = .55 * riskAgreement + .25 * (bottleneck || slowdown < .20 || projectedRisk.ordinal() < RiskLevel.HIGH.ordinal() ? 1 : .65)
                + .20 * (movementAgreement ? 1 : .5);
        return roundConfidence(clamp(.25 * sample + .25 * history + .20 * recency + .15 * fit + .15 * signalAgreement), null);
    }

    private static double trendFit(List<RiskEvent> readings, boolean density) {
        double mean = readings.stream().mapToDouble(event -> density ? event.getDensityScore() : event.getMovementSpeed()).average().orElse(0);
        double total = readings.stream().mapToDouble(event -> Math.pow((density ? event.getDensityScore() : event.getMovementSpeed()) - mean, 2)).sum();
        if (total <= 0) return 1;
        double slope = boundedSlope(readings, density);
        double intercept = mean - slope * readings.stream().mapToDouble(event -> event.getTimestamp().toEpochMilli() / 1000.0).average().orElse(0);
        double residual = readings.stream().mapToDouble(event -> {
            double time = event.getTimestamp().toEpochMilli() / 1000.0;
            double value = density ? event.getDensityScore() : event.getMovementSpeed();
            return Math.pow(value - (intercept + slope * time), 2);
        }).sum();
        return clamp(1 - residual / total);
    }

    private static RiskLevel eventRisk(RiskEvent event) {
        double density = event.getDensityScore();
        return density >= CRITICAL_DENSITY ? RiskLevel.CRITICAL : density >= HIGH_DENSITY ? RiskLevel.HIGH : density >= MEDIUM_DENSITY ? RiskLevel.MEDIUM : RiskLevel.LOW;
    }

    private static RiskForecastState stabilizeState(List<RiskEvent> readings, RiskForecastState raw, RiskForecastState previous) {
        if (previous == null || previous == RiskForecastState.INSUFFICIENT_DATA) {
            return enterSupported(readings, raw) ? raw : RiskForecastState.STABLE;
        }
        if (previous == RiskForecastState.CRUSH_RISK || previous == RiskForecastState.SURGE_RISK) {
            if (raw == RiskForecastState.CRUSH_RISK || raw == RiskForecastState.SURGE_RISK) return raw.ordinal() >= previous.ordinal() ? raw : previous;
            return recoverySupported(readings) ? raw : previous;
        }
        if (previous == RiskForecastState.RISING && (raw == RiskForecastState.SURGE_RISK || raw == RiskForecastState.CRUSH_RISK)) {
            return enterSupported(readings, raw) ? raw : previous;
        }
        if (raw == RiskForecastState.RISING || raw == RiskForecastState.SURGE_RISK || raw == RiskForecastState.CRUSH_RISK) {
            return enterSupported(readings, raw) ? raw : previous;
        }
        return recoverySupported(readings) ? raw : previous;
    }

    private static boolean enterSupported(List<RiskEvent> readings, RiskForecastState state) {
        if (readings.size() < 2) return false;
        RiskEvent previous = readings.get(readings.size() - 2);
        RiskEvent latest = readings.get(readings.size() - 1);
        boolean rising = latest.getDensityScore() >= previous.getDensityScore();
        return switch (state) {
            case RISING -> readings.size() >= 3
                    && readings.get(readings.size() - 2).getDensityScore() > readings.get(readings.size() - 3).getDensityScore()
                    && rising;
            case SURGE_RISK -> rising && (eventRisk(previous).ordinal() >= RiskLevel.HIGH.ordinal() || previous.getRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal())
                    && (eventRisk(latest).ordinal() >= RiskLevel.HIGH.ordinal() || latest.getRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal());
            case CRUSH_RISK -> rising && latest.getMovementSlowdown() >= .20 && (latest.getHotspotPersistenceSeconds() > 0 || latest.getMovementSlowdown() >= .30);
            default -> false;
        };
    }

    private static boolean recoverySupported(List<RiskEvent> readings) {
        if (readings.size() < 2) return false;
        RiskEvent first = readings.get(readings.size() - 2);
        RiskEvent latest = readings.get(readings.size() - 1);
        return latest.getDensityScore() <= 3.5 && latest.getDensityScore() <= first.getDensityScore()
                && latest.getRiskLevel().ordinal() <= RiskLevel.MEDIUM.ordinal()
                && Duration.between(first.getTimestamp(), latest.getTimestamp()).getSeconds() >= HYSTERESIS_SECONDS;
    }

    private static RiskLevel heldProjectedRisk(RiskLevel projected, RiskForecastState state) {
        if (state == RiskForecastState.CRUSH_RISK) return max(projected, RiskLevel.CRITICAL);
        if (state == RiskForecastState.SURGE_RISK) return max(projected, RiskLevel.HIGH);
        return projected;
    }

    private static double stabilizeConfidence(double candidate, double previous) {
        return roundConfidence(candidate, previous);
    }

    private static double roundConfidence(double candidate, Double previous) {
        double rounded = Math.round(clamp(candidate) * 100) / 100.0;
        return previous != null && Math.abs(rounded - previous) < .02 ? previous : rounded;
    }

    private static String explanation(RiskForecastState state, RiskLevel projected, double slope, double slowdown,
                                      long hotspotPersistence, Long seconds, double confidence) {
        String timing = seconds == null ? "within the next 10 minutes" : "in approximately " + seconds + " seconds";
        return switch (state) {
            case RECOVERING -> "Density is falling and the zone is recovering; no higher risk threshold is currently projected. Confidence " + percent(confidence) + ".";
            case RISING, SURGE_RISK, CRUSH_RISK -> "Projected " + projected + " " + timing + " because density is changing at " + format(slope * 60) + " people/m² per minute, movement slowdown is " + percent(slowdown) + ", and the hotspot has persisted for " + hotspotPersistence + " seconds. Confidence " + percent(confidence) + ".";
            default -> "Recent density and movement signals are stable; no imminent higher risk threshold is projected. Confidence " + percent(confidence) + ".";
        };
    }

    private static RiskForecastResponse withAnalysis(RiskForecastResponse forecast, RiskLevel projected,
                                                     RiskForecastState state, double confidence, String explanation) {
        return new RiskForecastResponse(forecast.zoneId(), forecast.zoneName(), forecast.generatedAt(), forecast.lastTelemetryAt(),
                forecast.currentRisk(), projected, forecast.forecastHorizonSeconds(), forecast.estimatedSecondsToProjectedRisk(),
                forecast.currentDensity(), forecast.projectedDensity(), forecast.densityTrendPerMinute(), forecast.currentMovementSpeed(),
                forecast.movementSlowdown(), forecast.movementSlowdownTrendPerMinute(), forecast.hotspotPersistenceSeconds(),
                forecast.bottleneckDetected(), round(confidence), state, explanation, forecast.source(), forecast.stale(), forecast.projections(),
                forecast.dominantDirection(), forecast.directionDegrees(), forecast.directionConfidence(), forecast.directionalConsistency(),
                forecast.reverseMovementRatio(), forecast.conflictingMovementRatio(), forecast.behaviorState(), forecast.behaviorExplanation(),
                forecast.analysisGeneratedAt(), forecast.analysisWindowStart(), forecast.analysisWindowEnd(), forecast.nextAnalysisAt(),
                forecast.analysisIntervalSeconds(), forecast.dataSufficiency(), forecast.flowState(), forecast.direction(), forecast.analysisPeopleCount(), forecast.analysisHotspotRegions());
    }

    private static String explanationFor(RiskForecastResponse forecast, RiskForecastState state,
                                         RiskLevel projected, double confidence) {
        String text = explanation(state, projected, forecast.densityTrendPerMinute() / 60.0,
                forecast.movementSlowdown(), forecast.hotspotPersistenceSeconds(),
                forecast.estimatedSecondsToProjectedRisk(), confidence);
        return state != forecast.state() && state != RiskForecastState.INSUFFICIENT_DATA
                ? "State held by the 20-second hysteresis window. " + text
                : text;
    }

    private record ForecastKey(Long zoneId, Long latestEventId, Instant latestTimestamp,
                               RiskEventSource latestSource, RiskEventSource sourceFilter, long fingerprint) {
        static ForecastKey from(Long zoneId, List<RiskEvent> readings, RiskEventSource sourceFilter) {
            RiskEvent latest = readings.isEmpty() ? null : readings.get(readings.size() - 1);
            long fingerprint = 1;
            for (RiskEvent event : readings) {
                fingerprint = 31 * fingerprint + Objects.hash(event.getId(), event.getTimestamp(), event.getDensityScore(),
                        event.getMovementSpeed(), event.getMovementSlowdown(), event.getRiskLevel(), event.getSource());
            }
            return new ForecastKey(zoneId, latest == null ? null : latest.getId(), latest == null ? null : latest.getTimestamp(),
                    latest == null ? null : latest.getSource(), sourceFilter, fingerprint);
        }
    }

    private record CachedForecast(ForecastKey key, RiskForecastResponse forecast,
                                  RiskForecastState heldState, double confidence) { }

    private static RiskLevel max(RiskLevel left, RiskLevel right) { return left.ordinal() >= right.ordinal() ? left : right; }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static double round(double value) { return Math.round(value * 10000.0) / 10000.0; }
    private static String percent(double value) { return Math.round(clamp(value) * 100) + "%"; }
    private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
}
