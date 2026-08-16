package com.nirikshan.service;

import com.nirikshan.model.RiskEvent;
import com.nirikshan.model.RiskEventSource;
import com.nirikshan.model.RiskForecastState;
import com.nirikshan.model.RiskLevel;
import com.nirikshan.model.Zone;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RiskForecastServiceTest {
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    @Test void refusesFewerThanFiveReadings() {
        RiskForecastService service = new RiskForecastService(null, null);
        var forecast = RiskForecastService.calculate(zone(), List.of(event(0, .5, RiskLevel.LOW), event(10, .6, RiskLevel.LOW), event(20, .6, RiskLevel.LOW), event(30, .6, RiskLevel.LOW)), BASE.plusSeconds(30));
        assertEquals(RiskForecastState.INSUFFICIENT_DATA, forecast.state());
        assertEquals(0, forecast.confidence());
    }

    @Test void stableLowDensityRemainsStable() {
        var forecast = RiskForecastService.calculate(zone(), List.of(event(0, .5, RiskLevel.LOW), event(10, .52, RiskLevel.LOW), event(20, .51, RiskLevel.LOW), event(30, .52, RiskLevel.LOW), event(40, .51, RiskLevel.LOW)), BASE.plusSeconds(40));
        assertEquals(RiskForecastState.STABLE, forecast.state());
        assertEquals(RiskLevel.LOW, forecast.projectedRisk());
        assertTrue(forecast.confidence() >= 0 && forecast.confidence() <= 1);
    }

    @Test void risingDensityProjectsHigherRiskAndTimeToThreshold() {
        var forecast = RiskForecastService.calculate(zone(), List.of(event(0, 1.0, RiskLevel.LOW), event(10, 1.5, RiskLevel.MEDIUM), event(20, 2.0, RiskLevel.MEDIUM), event(30, 2.5, RiskLevel.MEDIUM), event(40, 3.0, RiskLevel.MEDIUM)), BASE.plusSeconds(40));
        assertTrue(forecast.projectedRisk().ordinal() >= RiskLevel.HIGH.ordinal());
        assertNotNull(forecast.estimatedSecondsToProjectedRisk());
        assertTrue(forecast.estimatedSecondsToProjectedRisk() >= 0 && forecast.estimatedSecondsToProjectedRisk() <= 600);
        assertEquals(RiskForecastState.RISING, forecast.state());
    }

    @Test void slowdownAndBottleneckAreIncludedInForecast() {
        Zone zone = zone(); zone.setBottleneckDetected(true);
        List<RiskEvent> events = List.of(event(0, 3.0, RiskLevel.MEDIUM, .10, 10), event(10, 3.3, RiskLevel.MEDIUM, .30, 20), event(20, 3.8, RiskLevel.MEDIUM, .45, 30), event(30, 4.2, RiskLevel.HIGH, .50, 40), event(40, 4.5, RiskLevel.HIGH, .55, 50));
        var forecast = RiskForecastService.calculate(zone, events, BASE.plusSeconds(40));
        assertTrue(forecast.movementSlowdown() >= .10);
        assertTrue(forecast.hotspotPersistenceSeconds() >= 30);
        assertTrue(forecast.bottleneckDetected());
        assertTrue(forecast.projectedRisk().ordinal() >= RiskLevel.HIGH.ordinal());
    }

    @Test void criticalProjectionAndRecoveryAreDistinguished() {
        var critical = RiskForecastService.calculate(zone(), List.of(event(0, 3.5, RiskLevel.MEDIUM, .10, 10), event(10, 4.0, RiskLevel.HIGH, .25, 20), event(20, 4.8, RiskLevel.HIGH, .30, 30), event(30, 5.3, RiskLevel.HIGH, .35, 40), event(40, 5.8, RiskLevel.HIGH, .40, 50)), BASE.plusSeconds(40));
        assertEquals(RiskLevel.CRITICAL, critical.projectedRisk());
        assertEquals(RiskForecastState.CRUSH_RISK, critical.state());
        var recovery = RiskForecastService.calculate(zone(), List.of(event(0, 4.5, RiskLevel.HIGH), event(10, 4.0, RiskLevel.MEDIUM), event(20, 3.5, RiskLevel.MEDIUM), event(30, 2.8, RiskLevel.MEDIUM), event(40, 2.2, RiskLevel.MEDIUM)), BASE.plusSeconds(40));
        assertEquals(RiskForecastState.RECOVERING, recovery.state());
    }

    @Test void staleTelemetryReturnsNeutralState() {
        var forecast = RiskForecastService.calculate(zone(), List.of(event(0, .5, RiskLevel.LOW), event(10, .6, RiskLevel.LOW), event(20, .7, RiskLevel.LOW), event(30, .7, RiskLevel.LOW), event(40, .7, RiskLevel.LOW)), BASE.plusSeconds(100));
        assertTrue(forecast.stale());
        assertEquals(RiskForecastState.INSUFFICIENT_DATA, forecast.state());
        assertTrue(forecast.explanation().toLowerCase().contains("stale"));
    }

    private static Zone zone() { Zone zone = new Zone(); zone.setId(1L); zone.setName("Test Zone"); zone.setCurrentRiskLevel(RiskLevel.LOW); zone.setCurrentDensity(0); zone.setLastUpdated(BASE); return zone; }
    private static RiskEvent event(long seconds, double density, RiskLevel level) { return event(seconds, density, level, 0, 0); }
    private static RiskEvent event(long seconds, double density, RiskLevel level, double slowdown, long hotspotPersistence) {
        RiskEvent event = new RiskEvent(); event.setTimestamp(BASE.plusSeconds(seconds)); event.setDensityScore(density); event.setMovementSpeed(Math.max(.2, 1.2 - slowdown)); event.setRiskLevel(level); event.setMovementSlowdown(slowdown); event.setHotspotPersistenceSeconds(hotspotPersistence); event.setSource(RiskEventSource.SIMULATION); return event;
    }
}
