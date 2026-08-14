package com.nirikshan.service;

import com.nirikshan.dto.*;
import com.nirikshan.model.*;
import com.nirikshan.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class RiskEventService {
    private final RiskEventRepository eventRepository;
    private final ZoneRepository zoneRepository;
    private final AlertRepository alertRepository;
    private final RecommendationService recommendationService;
    private final SimpMessagingTemplate messagingTemplate;
    public RiskEventService(RiskEventRepository eventRepository, ZoneRepository zoneRepository, AlertRepository alertRepository, RecommendationService recommendationService, SimpMessagingTemplate messagingTemplate) {
        this.eventRepository = eventRepository; this.zoneRepository = zoneRepository; this.alertRepository = alertRepository; this.recommendationService = recommendationService; this.messagingTemplate = messagingTemplate;
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
        RiskEvent saved = eventRepository.save(event);
        zone.setCurrentDensity(request.densityScore()); zone.setCurrentPeopleCount(request.peopleCount()); zone.setCurrentRiskLevel(request.riskLevel()); zone.setLastUpdated(request.timestamp());
        zoneRepository.save(zone);
        recommendationService.evaluate(zone, saved, previousRisk, previousEvent);
        RiskEventResponse response = toResponse(saved);
        messagingTemplate.convertAndSend("/topic/risk-updates", response);
        if (request.riskLevel().ordinal() >= RiskLevel.HIGH.ordinal()) {
            Alert alert = alertRepository.save(new Alert(zone, request.timestamp(), request.explanation(), request.riskLevel()));
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
    private RiskEventResponse toResponse(RiskEvent e) { return new RiskEventResponse(e.getId(), e.getZone().getId(), e.getTimestamp(), e.getDensityScore(), e.getPeopleCount(), e.getMovementSpeed(), e.getRiskLevel(), e.getExplanation(), e.getSourceClipId()); }
    private AlertResponse toResponse(Alert a) { return new AlertResponse(a.getId(), a.getZone().getId(), a.getZone().getName(), a.getTimestamp(), a.getMessage(), a.getSeverity(), a.isResolved(), a.getResolvedAt()); }
}
