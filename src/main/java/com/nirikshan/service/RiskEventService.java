package com.nirikshan.service;

import com.nirikshan.dto.*;
import com.nirikshan.model.*;
import com.nirikshan.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;

@Service
public class RiskEventService {
    private final RiskEventRepository eventRepository;
    private final ZoneRepository zoneRepository;
    private final AlertRepository alertRepository;
    private final RecommendationService recommendationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final RiskForecastService forecastService;
    private final FlowBehaviorService flowBehaviorService;
    public RiskEventService(RiskEventRepository eventRepository, ZoneRepository zoneRepository, AlertRepository alertRepository, RecommendationService recommendationService, SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper, RiskForecastService forecastService, FlowBehaviorService flowBehaviorService) {
        this.eventRepository = eventRepository; this.zoneRepository = zoneRepository; this.alertRepository = alertRepository; this.recommendationService = recommendationService; this.messagingTemplate = messagingTemplate; this.objectMapper = objectMapper; this.forecastService = forecastService; this.flowBehaviorService = flowBehaviorService;
    }

    @Transactional
    public RiskEventResponse ingest(RiskEventRequest request) {
        Zone zone = zoneRepository.findById(request.zoneId()).orElseThrow(() -> new ResourceNotFoundException("Zone", request.zoneId()));
        RiskLevel previousRisk = zone.getCurrentRiskLevel();
        RiskEvent previousEvent = eventRepository.findByZoneIdOrderByTimestampDesc(zone.getId(), PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        RiskEvent event = new RiskEvent();
        event.setZone(zone); event.setTimestamp(request.timestamp()); event.setDensityScore(request.densityScore()); event.setPeopleCount(request.peopleCount());
        event.setMovementSpeed(request.movementSpeed()); event.setRiskLevel(request.riskLevel());
        event.setExplanation(request.explanation()); event.setSourceClipId(request.sourceClipId());
        event.setSource(parseSource(request.source(), request.sourceClipId()));
        event.setDominantDirection(request.dominantDirection());
        event.setDirectionDegrees(request.directionDegrees());
        event.setDirectionConfidence(request.directionConfidence() == null ? 0 : request.directionConfidence());
        event.setDirectionalConsistency(request.directionalConsistency() == null ? 0 : request.directionalConsistency());
        event.setReverseMovementRatio(request.reverseMovementRatio() == null ? 0 : request.reverseMovementRatio());
        event.setConflictingMovementRatio(request.conflictingMovementRatio() == null ? 0 : request.conflictingMovementRatio());
        event.setHotspotRegions(writeHotspots(request.hotspotRegions()));
        event.setDensityChange(request.densityChange() == null ? relativeChange(previousEvent == null ? 0 : previousEvent.getDensityScore(), request.densityScore()) : request.densityChange());
        event.setMovementSlowdown(request.movementSlowdown() == null ? relativeDrop(previousEvent == null ? request.movementSpeed() : previousEvent.getMovementSpeed(), request.movementSpeed()) : request.movementSlowdown());
        event.setHotspotPersistenceSeconds(request.hotspotPersistenceSeconds() == null ? hotspotPersistenceSeconds(zone.getId(), request.timestamp(), request.hotspotRegions()) : Math.max(0, Math.round(request.hotspotPersistenceSeconds())));
        FlowBehaviorService.Analysis flow = flowBehaviorService.analyze(zone.getId(), request, previousEvent);
        event.setBehaviorState(flow.state());
        event.setBehaviorExplanation(request.behaviorExplanation() == null || request.behaviorExplanation().isBlank() ? flow.explanation() : request.behaviorExplanation());
        if (flow.state() == com.nirikshan.model.FlowBehaviorState.INSUFFICIENT_DATA) {
            event.setDominantDirection(event.getDominantDirection() == null ? "INSUFFICIENT_DATA" : event.getDominantDirection());
        }
        RiskEvent saved = eventRepository.save(event);
        zone.setCurrentDensity(request.densityScore()); zone.setCurrentPeopleCount(request.peopleCount()); zone.setCurrentRiskLevel(request.riskLevel()); zone.setLastUpdated(request.timestamp());
        updateBottleneck(zone);
        zoneRepository.save(zone);
        recommendationService.evaluate(zone, saved, previousRisk, previousEvent);
        if (request.riskLevel() == RiskLevel.LOW) resolveStaleAlerts(zone, request.timestamp());
        RiskEventResponse response = toResponse(saved);
        messagingTemplate.convertAndSend("/topic/risk-updates", response);
        var forecast = forecastService.forecast(zone.getId());
        messagingTemplate.convertAndSend("/topic/risk-forecasts", forecast);
        messagingTemplate.convertAndSend("/topic/risk-intelligence", forecast);
        if (request.riskLevel().ordinal() >= RiskLevel.HIGH.ordinal()
                || (forecast.stampedeLikelihood() != null && "HIGH".equals(forecast.stampedeLikelihood().level()))) {
            RiskLevel alertSeverity = request.riskLevel().ordinal() >= RiskLevel.HIGH.ordinal() ? request.riskLevel() : RiskLevel.HIGH;
            String alertMessage = forecast.stampedeLikelihood() != null && "HIGH".equals(forecast.stampedeLikelihood().level())
                    ? "Heuristic stampede likelihood HIGH: " + forecast.stampedeLikelihood().explanation() : request.explanation();
            Alert alert = upsertActiveAlert(zone, request.timestamp(), alertMessage, alertSeverity, saved.getSource());
            messagingTemplate.convertAndSend("/topic/alerts", toResponse(alert));
        }
        return response;
    }
    @Transactional(readOnly = true)
    public List<RiskEventResponse> recent(Long zoneId, int limit) {
        if (!zoneRepository.existsById(zoneId)) throw new ResourceNotFoundException("Zone", zoneId);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return eventRepository.findByZoneIdOrderByTimestampDesc(zoneId, PageRequest.of(0, safeLimit)).stream().map(this::toResponse).toList();
    }
    @Transactional(readOnly = true)
    public List<RiskEventResponse> recentVenue(Long venueId, int limit) {
        if (!zoneRepository.existsByVenueId(venueId)) throw new ResourceNotFoundException("Venue", venueId);
        int safeLimit = Math.max(1, Math.min(limit, 300));
        return eventRepository.findByZoneVenueIdOrderByTimestampDesc(venueId, PageRequest.of(0, safeLimit)).stream().map(this::toResponse).toList();
    }
    @Transactional(readOnly = true)
    public List<RiskEventResponse> hotspots(Long zoneId, int limit) {
        if (!zoneRepository.existsById(zoneId)) throw new ResourceNotFoundException("Zone", zoneId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return eventRepository.findByZoneIdOrderByTimestampDesc(zoneId, PageRequest.of(0, safeLimit)).stream().filter(e -> !readHotspots(e).isEmpty()).map(this::toResponse).toList();
    }
    private void updateBottleneck(Zone zone) {
        List<RiskEvent> recent = eventRepository.findTop10ByZoneIdOrderByTimestampDesc(zone.getId());
        if (recent.size() < 10) { zone.setBottleneckDetected(false); return; }
        long hotspotEvents = recent.stream().filter(e -> !readHotspots(e).isEmpty()).count();
        long slowdownEvents = recent.stream().filter(e -> e.getMovementSlowdown() >= 0.20).count();
        zone.setBottleneckDetected(hotspotEvents >= 7 && slowdownEvents >= 5 && recent.get(0).getDensityScore() >= recent.get(9).getDensityScore());
    }
    private long hotspotPersistenceSeconds(Long zoneId, java.time.Instant timestamp, List<HotspotRegion> current) {
        if (current == null || current.isEmpty()) return 0;
        List<RiskEvent> recent = eventRepository.findTop10ByZoneIdOrderByTimestampDesc(zoneId);
        java.time.Instant start = timestamp;
        for (RiskEvent prior : recent) {
            if (prior.getHotspotRegions() == null || readHotspots(prior).isEmpty()) break;
            start = prior.getTimestamp();
        }
        return Math.max(0, java.time.Duration.between(start, timestamp).toSeconds());
    }
    private double relativeChange(double oldValue, double newValue) { return oldValue <= 0 ? 0 : Math.max(0, (newValue - oldValue) / oldValue); }
    private double relativeDrop(double oldValue, double newValue) { return oldValue <= 0 ? 0 : Math.max(0, (oldValue - newValue) / oldValue); }
    private void resolveStaleAlerts(Zone zone, java.time.Instant timestamp) {
        alertRepository.findByZoneIdAndResolvedFalseOrderByTimestampDesc(zone.getId()).stream()
                .filter(alert -> !alert.isResolved()).forEach(alert -> {
                    alert.setResolved(true); alert.setResolvedAt(timestamp); alertRepository.save(alert);
                    messagingTemplate.convertAndSend("/topic/alerts", toResponse(alert));
                });
    }
    private Alert upsertActiveAlert(Zone zone, java.time.Instant timestamp, String message, RiskLevel severity, RiskEventSource source) {
        List<Alert> active = alertRepository.findByZoneIdAndResolvedFalseOrderByTimestampDesc(zone.getId());
        Alert alert = active.isEmpty() ? new Alert(zone, timestamp, message, severity, source) : active.get(0);
        alert.setTimestamp(timestamp); alert.setMessage(message); alert.setSeverity(severity); alert.setResolved(false); alert.setResolvedAt(null);
        alert.setSource(source);
        for (Alert duplicate : active.stream().skip(1).toList()) {
            duplicate.setResolved(true); duplicate.setResolvedAt(timestamp); alertRepository.save(duplicate);
            messagingTemplate.convertAndSend("/topic/alerts", toResponse(duplicate));
        }
        return alertRepository.save(alert);
    }
    private String writeHotspots(List<HotspotRegion> hotspots) {
        try { return objectMapper.writeValueAsString(hotspots == null ? Collections.emptyList() : hotspots); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid hotspot data", e); }
    }
    private List<HotspotRegion> readHotspots(RiskEvent event) {
        if (event.getHotspotRegions() == null || event.getHotspotRegions().isBlank()) return List.of();
        try { return objectMapper.readValue(event.getHotspotRegions(), new TypeReference<>() {}); }
        catch (JsonProcessingException e) { return List.of(); }
    }
    @Transactional
    public void restoreZoneFromLive(Long zoneId) {
        Zone zone = zoneRepository.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("Zone", zoneId));
        recommendationService.clearZoneState(zoneId);
        alertRepository.findByZoneIdAndResolvedFalse(zoneId).forEach(alert -> { alert.setResolved(true); alert.setResolvedAt(java.time.Instant.now()); alertRepository.save(alert); messagingTemplate.convertAndSend("/topic/alerts", toResponse(alert)); });
        RiskEvent latest = eventRepository.findFirstByZoneIdAndSourceOrderByTimestampDesc(zoneId, RiskEventSource.LIVE).orElse(null);
        if (latest == null) {
            zone.setCurrentDensity(0); zone.setCurrentPeopleCount(0); zone.setCurrentRiskLevel(RiskLevel.LOW); zone.setLastUpdated(java.time.Instant.now()); zone.setBottleneckDetected(false); zoneRepository.save(zone);
            messagingTemplate.convertAndSend("/topic/risk-updates", new RiskEventResponse(null, zoneId, zone.getLastUpdated(), 0, 0, 0, RiskLevel.LOW, "No live telemetry; simulation ended and this zone is offline.", List.of(), false, null, 0, 0, 0, "LIVE", "INSUFFICIENT_DATA", null, 0, 0, 0, 0, com.nirikshan.model.FlowBehaviorState.INSUFFICIENT_DATA, "No live telemetry is available."));
            messagingTemplate.convertAndSend("/topic/risk-forecasts", forecastService.offline(zoneId));
            return;
        }
        zone.setCurrentDensity(latest.getDensityScore()); zone.setCurrentPeopleCount(latest.getPeopleCount()); zone.setCurrentRiskLevel(latest.getRiskLevel()); zone.setLastUpdated(latest.getTimestamp()); zone.setBottleneckDetected(false); zoneRepository.save(zone);
        messagingTemplate.convertAndSend("/topic/risk-updates", toResponse(latest));
        messagingTemplate.convertAndSend("/topic/risk-forecasts", forecastService.forecastLive(zoneId));
    }
    private RiskEventSource parseSource(String source, String clipId) {
        if (source != null && source.equalsIgnoreCase("SIMULATION")) return RiskEventSource.SIMULATION;
        return clipId != null && clipId.startsWith("DEMO_REPLAY_") ? RiskEventSource.SIMULATION : RiskEventSource.LIVE;
    }
    private RiskEventResponse toResponse(RiskEvent e) {
        boolean flowAvailable = e.getBehaviorState() != null
                && e.getBehaviorState() != FlowBehaviorState.INSUFFICIENT_DATA
                && e.getDominantDirection() != null
                && e.getDirectionDegrees() != null
                && e.getDirectionConfidence() > 0;
        return new RiskEventResponse(e.getId(), e.getZone().getId(), e.getTimestamp(), e.getDensityScore(), e.getPeopleCount(), e.getMovementSpeed(), e.getRiskLevel(), e.getExplanation(), readHotspots(e), e.getZone().isBottleneckDetected(), e.getSourceClipId(), e.getDensityChange(), e.getMovementSlowdown(), e.getHotspotPersistenceSeconds(), e.getSource().name(), flowAvailable ? e.getDominantDirection() : null, flowAvailable ? e.getDirectionDegrees() : null, flowAvailable ? e.getDirectionConfidence() : 0, flowAvailable ? e.getDirectionalConsistency() : 0, flowAvailable ? e.getReverseMovementRatio() : 0, flowAvailable ? e.getConflictingMovementRatio() : 0, flowAvailable ? e.getBehaviorState() : FlowBehaviorState.INSUFFICIENT_DATA, flowAvailable ? e.getBehaviorExplanation() : "Insufficient valid movement data is available for a reliable flow estimate.");
    }
    private AlertResponse toResponse(Alert a) { return new AlertResponse(a.getId(), a.getZone().getId(), a.getZone().getName(), a.getTimestamp(), a.getMessage(), a.getSeverity(), a.isResolved(), a.getResolvedAt(), a.getSource()); }
}
