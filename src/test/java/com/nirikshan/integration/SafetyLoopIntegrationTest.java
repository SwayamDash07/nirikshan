package com.nirikshan.integration;

import com.nirikshan.dto.HotspotRegion;
import com.nirikshan.dto.RiskEventRequest;
import com.nirikshan.dto.RiskEventResponse;
import com.nirikshan.model.*;
import com.nirikshan.repository.AlertRepository;
import com.nirikshan.repository.RecommendationRepository;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.ZoneRepository;
import com.nirikshan.service.RiskEventService;
import com.nirikshan.service.RiskForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class SafetyLoopIntegrationTest {
    @Autowired RiskEventService riskEvents;
    @Autowired ZoneRepository zones;
    @Autowired RiskEventRepository events;
    @Autowired AlertRepository alerts;
    @Autowired RecommendationRepository recommendations;
    @Autowired RiskForecastService forecasts;

    private Long zoneId;
    private Instant base;

    @BeforeEach
    void prepare() {
        zoneId = zones.findAll().get(0).getId();
        events.deleteAll(); alerts.deleteAll(); recommendations.deleteAll();
        Zone zone = zones.findById(zoneId).orElseThrow();
        zone.setCurrentDensity(0); zone.setCurrentPeopleCount(0); zone.setCurrentRiskLevel(RiskLevel.LOW); zone.setBottleneckDetected(false); zone.setLastUpdated(Instant.now()); zones.save(zone);
        base = Instant.parse("2026-01-01T00:00:00Z");
    }

    @Test
    void ingestionDerivesSignalsSerializesHotspotsAndLabelsSimulation() {
        ingest(0, 1, 20, 1.0, RiskLevel.LOW, List.of(), "LIVE");
        RiskEventResponse response = ingest(5, 2, 40, .5, RiskLevel.HIGH, List.of(new HotspotRegion("2,2", 2.2)), "SIMULATION");
        assertEquals(1.0, response.densityChange(), .001);
        assertEquals(.5, response.movementSlowdown(), .001);
        assertEquals("SIMULATION", response.source());
        assertEquals("2,2", response.hotspotRegions().get(0).gridPosition());
    }

    @Test
    void persistentHotspotAndSlowdownSetBottleneckAndCreateOneAlertAndRecommendation() {
        double[] speeds = {1.0, .79, .6, .4, .3, .2, .2, .2, .2, .2};
        for (int i = 0; i < 10; i++) ingest(i * 5L, 3 + i * .1, 60, speeds[i], RiskLevel.HIGH, List.of(new HotspotRegion("2,2", 2.4)), "SIMULATION");
        Zone zone = zones.findById(zoneId).orElseThrow();
        long hotspots = events.findTop10ByZoneIdOrderByTimestampDesc(zoneId).stream().filter(event -> event.getHotspotRegions() != null && !event.getHotspotRegions().isBlank()).count();
        long slowdowns = events.findTop10ByZoneIdOrderByTimestampDesc(zoneId).stream().filter(event -> event.getMovementSlowdown() >= .20).count();
        assertTrue(zone.isBottleneckDetected(), "hotspots=" + hotspots + ", slowdowns=" + slowdowns + ", latest=" + zone.getCurrentDensity());
        assertEquals(1, alerts.findByZoneIdAndResolvedFalse(zoneId).size());
        assertEquals(1, recommendations.findByZoneIdAndStatus(zoneId, RecommendationStatus.PENDING).size());
        assertEquals(RecommendationType.CLOSE_ENTRY, recommendations.findByZoneIdAndStatus(zoneId, RecommendationStatus.PENDING).get(0).getType());
        assertEquals(RiskEventSource.SIMULATION, alerts.findByZoneIdAndResolvedFalse(zoneId).get(0).getSource());
        assertEquals(RiskEventSource.SIMULATION, recommendations.findByZoneIdAndStatus(zoneId, RecommendationStatus.PENDING).get(0).getSource());
    }

    @Test
    void repeatedHighReadingsDoNotDuplicateActiveAlertOrRecommendation() {
        for (int i = 0; i < 6; i++) ingest(i * 5L, 4, 70, .6, RiskLevel.HIGH, List.of(), "LIVE");
        assertEquals(1, alerts.findByZoneIdAndResolvedFalse(zoneId).size());
        assertTrue(recommendations.findByZoneIdAndStatus(zoneId, RecommendationStatus.PENDING).size() <= 1);
    }

    @Test
    void lowRiskRecoveryResolvesAlertAndExpiresRecommendation() {
        for (int i = 0; i < 6; i++) ingest(i * 5L, 4, 70, .6, RiskLevel.HIGH, List.of(), "LIVE");
        ingest(70, .3, 5, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(75, .3, 5, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(95, .3, 5, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        assertTrue(alerts.findByZoneIdAndResolvedFalse(zoneId).isEmpty());
        assertTrue(recommendations.findByZoneIdAndStatus(zoneId, RecommendationStatus.PENDING).isEmpty());
    }

    @Test
    void simulationCleanupRestoresLatestLiveState() {
        ingest(0, .4, 8, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(5, 5, 100, .4, RiskLevel.HIGH, List.of(new HotspotRegion("1,1", 2)), "SIMULATION");
        riskEvents.restoreZoneFromLive(zoneId);
        Zone zone = zones.findById(zoneId).orElseThrow();
        assertEquals(.4, zone.getCurrentDensity(), .001);
        assertEquals(RiskLevel.LOW, zone.getCurrentRiskLevel());
        assertTrue(alerts.findByZoneIdAndResolvedFalse(zoneId).isEmpty());
        assertEquals(RiskEventSource.LIVE, events.findFirstByZoneIdAndSourceOrderByTimestampDesc(zoneId, RiskEventSource.LIVE).orElseThrow().getSource());
        assertEquals(RiskEventSource.LIVE, forecasts.forecast(zoneId).source());
        assertEquals(RiskForecastState.INSUFFICIENT_DATA, forecasts.forecast(zoneId).state());
        assertTrue(forecasts.forecast(zoneId).stale());
    }

    @Test
    void insufficientFlowNeverExposesDirectionOrConfidence() {
        base = Instant.now().minusSeconds(5);
        RiskEventResponse event = ingest(0, .5, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        assertEquals(FlowBehaviorState.INSUFFICIENT_DATA, event.behaviorState());
        assertNull(event.dominantDirection());
        assertNull(event.directionDegrees());
        assertEquals(0, event.directionConfidence());
        assertEquals(0, event.reverseMovementRatio());
        assertEquals(0, event.conflictingMovementRatio());

        var forecast = forecasts.forecast(zoneId);
        assertEquals(FlowBehaviorState.INSUFFICIENT_DATA, forecast.flowState());
        assertEquals("Unknown", forecast.dominantDirection());
        assertNull(forecast.direction());
        assertNull(forecast.directionDegrees());
        assertEquals(0, forecast.directionConfidence());
    }

    @Test
    void validFlowRequiresConfiguredSampleCountAndTimeSpan() {
        base = Instant.now().minusSeconds(20);
        ingestFlow(0);
        ingestFlow(5);
        ingestFlow(10);
        var sufficient = forecasts.forecast(zoneId);
        assertEquals("SUFFICIENT", sufficient.dataSufficiency());
        assertEquals("E", sufficient.direction());
        assertEquals(90, sufficient.directionDegrees(), .01);
        assertTrue(sufficient.directionConfidence() > 0);

        events.deleteAll();
        ingestFlow(0);
        ingestFlow(2);
        ingestFlow(4);
        var shortWindow = forecasts.forecast(zoneId);
        assertEquals(FlowBehaviorState.INSUFFICIENT_DATA, shortWindow.flowState());
        assertNull(shortWindow.direction());
        assertEquals(0, shortWindow.directionConfidence());
    }

    @Test
    void risingLiveReadingsCreateProjectedActionsWithoutContradictoryRouteAdvice() {
        base = Instant.now().minusSeconds(40);
        ingest(0, 1.0, 20, 1.0, RiskLevel.LOW, List.of(), "LIVE");
        ingest(10, 2.0, 40, .8, RiskLevel.MEDIUM, List.of(), "LIVE");
        ingest(20, 3.0, 60, .6, RiskLevel.MEDIUM, List.of(), "LIVE");
        ingest(30, 4.0, 80, .4, RiskLevel.HIGH, List.of(), "LIVE");
        ingest(40, 5.0, 100, .3, RiskLevel.HIGH, List.of(), "LIVE");
        var forecast = forecasts.forecast(zoneId);
        assertEquals(RiskLevel.CRITICAL, forecast.projectedRisk(), forecast.toString());
        List<Recommendation> pending = recommendations.findByZoneIdAndStatus(zoneId, RecommendationStatus.PENDING);
        assertEquals(1, pending.size(), "A zone must have one current response action, not competing queue entries");
        assertTrue(pending.stream().anyMatch(item -> item.getType() == RecommendationType.CLOSE_ENTRY), pending.stream().map(item -> item.getType() + ":" + item.getMessage()).toList().toString());
        assertTrue(pending.stream().allMatch(item -> !item.getMessage().contains("Redirect incoming visitors")));
        assertTrue(pending.stream().anyMatch(item -> item.getMessage().contains("Projected")));
    }

    @Test
    void repeatedForecastCallsKeepAnalyticalValuesStable() throws InterruptedException {
        base = Instant.now().minusSeconds(40);
        ingest(0, .5, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(10, .52, 11, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(20, .51, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(30, .53, 11, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(40, .52, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        var first = forecasts.forecast(zoneId);
        Thread.sleep(1100);
        var second = forecasts.forecast(zoneId);
        assertEquals(first.currentRisk(), second.currentRisk());
        assertEquals(first.projectedRisk(), second.projectedRisk());
        assertEquals(first.confidence(), second.confidence());
        assertEquals(first.state(), second.state());
        assertEquals(first.projectedDensity(), second.projectedDensity());
        assertEquals(first.estimatedSecondsToProjectedRisk(), second.estimatedSecondsToProjectedRisk());
        assertEquals(first.explanation(), second.explanation());
        assertEquals(first.generatedAt(), second.generatedAt());
        assertEquals(first.analysisGeneratedAt(), second.analysisGeneratedAt());
        assertTrue(second.nextAnalysisAt().isAfter(second.analysisGeneratedAt()));
    }

    @Test
    void oneNoisyReadingDoesNotFlipStateBeforeTheNextAnalysisWindow() {
        base = Instant.now().minusSeconds(50);
        ingest(0, .5, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(10, .51, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(20, .50, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(30, .52, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(40, .51, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        assertEquals(RiskForecastState.STABLE, forecasts.forecast(zoneId).state());
        ingest(50, 4.0, 80, .4, RiskLevel.HIGH, List.of(new HotspotRegion("2,2", 2)), "LIVE");
        assertEquals(RiskForecastState.STABLE, forecasts.forecast(zoneId).state());
        ingest(60, 4.8, 100, .2, RiskLevel.HIGH, List.of(new HotspotRegion("2,2", 2.4)), "LIVE");
        var transitioned = forecasts.forecast(zoneId);
        assertEquals(RiskForecastState.STABLE, transitioned.state(), "Non-critical changes remain held until the next analysis window");
        ingest(80, 6.5, 130, .1, RiskLevel.CRITICAL, List.of(new HotspotRegion("2,2", 3)), "LIVE");
        var critical = forecasts.forecast(zoneId);
        assertTrue(critical.state() == RiskForecastState.SURGE_RISK || critical.state() == RiskForecastState.CRUSH_RISK, critical.toString());
    }

    @Test
    void recoveryRequiresTwentySecondsOfImprovingTelemetry() {
        base = Instant.now().minusSeconds(50);
        ingest(0, 4.0, 80, .8, RiskLevel.HIGH, List.of(new HotspotRegion("2,2", 2)), "LIVE");
        ingest(10, 4.3, 85, .6, RiskLevel.HIGH, List.of(new HotspotRegion("2,2", 2)), "LIVE");
        ingest(20, 4.6, 90, .4, RiskLevel.HIGH, List.of(new HotspotRegion("2,2", 2)), "LIVE");
        ingest(30, 4.9, 95, .3, RiskLevel.HIGH, List.of(new HotspotRegion("2,2", 2)), "LIVE");
        ingest(40, 5.2, 100, .2, RiskLevel.HIGH, List.of(new HotspotRegion("2,2", 2)), "LIVE");
        assertEquals(RiskForecastState.CRUSH_RISK, forecasts.forecast(zoneId).state());
        ingest(50, 3.0, 50, 1.0, RiskLevel.MEDIUM, List.of(), "LIVE");
        assertEquals(RiskForecastState.CRUSH_RISK, forecasts.forecast(zoneId).state());
        ingest(70, 2.0, 30, 1.1, RiskLevel.LOW, List.of(), "LIVE");
        assertEquals(RiskForecastState.RECOVERING, forecasts.forecast(zoneId).state());
    }

    @Test
    void newTelemetryInvalidatesCacheAndCanChangeConfidence() {
        base = Instant.now().minusSeconds(40);
        ingest(0, .5, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(10, .52, 11, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(20, .51, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(30, .53, 11, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        ingest(40, .52, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        var before = forecasts.forecast(zoneId);
        ingest(50, .52, 10, 1.2, RiskLevel.LOW, List.of(), "LIVE");
        var after = forecasts.forecast(zoneId);
        assertNotEquals(before.lastTelemetryAt(), after.lastTelemetryAt());
        assertEquals(before.confidence(), after.confidence());
        assertEquals(before.analysisGeneratedAt(), after.analysisGeneratedAt());
    }

    private RiskEventResponse ingest(long seconds, double density, int people, double speed, RiskLevel risk, List<HotspotRegion> hotspots, String source) {
        return riskEvents.ingest(new RiskEventRequest(zoneId, base.plusSeconds(seconds), density, people, speed, risk,
                "test signal", hotspots, "test-clip", null, null, null, source));
    }

    private RiskEventResponse ingestFlow(long seconds) {
        return riskEvents.ingest(new RiskEventRequest(zoneId, base.plusSeconds(seconds), .5, 10, 1.2, RiskLevel.LOW,
                "test flow", List.of(), "test-flow", 0.0, 0.0, 0.0, "LIVE", "E", 90.0, .8, .8, 0.0, 0.0,
                FlowBehaviorState.NORMAL_FLOW, "Stable eastbound movement."));
    }
}
