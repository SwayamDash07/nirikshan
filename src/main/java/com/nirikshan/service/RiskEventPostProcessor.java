package com.nirikshan.service;

import com.nirikshan.dto.AlertResponse;
import com.nirikshan.dto.RiskForecastResponse;
import com.nirikshan.model.*;
import com.nirikshan.repository.AlertRepository;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.ZoneRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

@Service
public class RiskEventPostProcessor {
    private final RiskEventRepository events;
    private final ZoneRepository zones;
    private final AlertRepository alerts;
    private final RecommendationService recommendations;
    private final RiskForecastService forecasts;
    private final SimpMessagingTemplate messaging;

    public RiskEventPostProcessor(RiskEventRepository events, ZoneRepository zones, AlertRepository alerts,
                                  RecommendationService recommendations, RiskForecastService forecasts,
                                  SimpMessagingTemplate messaging) {
        this.events = events;
        this.zones = zones;
        this.alerts = alerts;
        this.recommendations = recommendations;
        this.forecasts = forecasts;
        this.messaging = messaging;
    }

    @Async("riskEventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(RiskEventIngestedEvent ingested) {
        RiskEvent event = events.findById(ingested.eventId()).orElse(null);
        Zone zone = zones.findById(ingested.zoneId()).orElse(null);
        if (event == null || zone == null) return;

        RiskEvent previous = ingested.previousEventId() == null ? null : events.findById(ingested.previousEventId()).orElse(null);
        recommendations.evaluate(zone, event, ingested.previousRisk(), previous);

        if (event.getRiskLevel() == RiskLevel.LOW) resolveStaleAlerts(zone, event.getTimestamp());
        RiskForecastResponse forecast = forecasts.forecast(zone.getId());
        messaging.convertAndSend("/topic/risk-forecasts", forecast);
        messaging.convertAndSend("/topic/risk-intelligence", forecast);

        if (event.getRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal()
                || (forecast.stampedeLikelihood() != null && "HIGH".equals(forecast.stampedeLikelihood().level()))) {
            RiskLevel severity = event.getRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal()
                    ? event.getRiskLevel() : RiskLevel.HIGH;
            String message = forecast.stampedeLikelihood() != null && "HIGH".equals(forecast.stampedeLikelihood().level())
                    ? "Heuristic stampede likelihood HIGH: " + forecast.stampedeLikelihood().explanation()
                    : event.getExplanation();
            Alert alert = upsertActiveAlert(zone, event.getTimestamp(), message, severity, event.getSource());
            messaging.convertAndSend("/topic/alerts", toResponse(alert));
        }
    }

    private void resolveStaleAlerts(Zone zone, Instant timestamp) {
        alerts.findByZoneIdAndResolvedFalseOrderByTimestampDesc(zone.getId()).stream()
                .filter(alert -> !alert.isResolved())
                .forEach(alert -> {
                    alert.setResolved(true);
                    alert.setResolvedAt(timestamp);
                    alerts.save(alert);
                    messaging.convertAndSend("/topic/alerts", toResponse(alert));
                });
    }

    private Alert upsertActiveAlert(Zone zone, Instant timestamp, String message, RiskLevel severity, RiskEventSource source) {
        List<Alert> active = alerts.findByZoneIdAndResolvedFalseOrderByTimestampDesc(zone.getId());
        Alert alert = active.isEmpty() ? new Alert(zone, timestamp, message, severity, source) : active.get(0);
        alert.setTimestamp(timestamp);
        alert.setMessage(message);
        alert.setSeverity(severity);
        alert.setResolved(false);
        alert.setResolvedAt(null);
        alert.setSource(source);
        for (Alert duplicate : active.stream().skip(1).toList()) {
            duplicate.setResolved(true);
            duplicate.setResolvedAt(timestamp);
            alerts.save(duplicate);
            messaging.convertAndSend("/topic/alerts", toResponse(duplicate));
        }
        return alerts.save(alert);
    }

    private AlertResponse toResponse(Alert alert) {
        return new AlertResponse(alert.getId(), alert.getZone().getId(), alert.getZone().getName(),
                alert.getTimestamp(), alert.getMessage(), alert.getSeverity(), alert.isResolved(),
                alert.getResolvedAt(), alert.getSource());
    }
}
