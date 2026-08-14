package com.nirikshan.service;

import com.nirikshan.dto.RecommendationResponse;
import com.nirikshan.model.*;
import com.nirikshan.repository.RecommendationRepository;
import com.nirikshan.repository.UserRepository;
import com.nirikshan.repository.ZoneRepository;
import com.nirikshan.security.CurrentUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class RecommendationService {
    private static final Duration COOLDOWN = Duration.ofMinutes(2);
    private static final Duration RAPID_INCREASE_WINDOW = Duration.ofSeconds(30);
    private static final double RAPID_DENSITY_INCREASE = 0.50;

    private final RecommendationRepository recommendations;
    private final ZoneRepository zones;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final SimpMessagingTemplate messaging;

    public RecommendationService(RecommendationRepository recommendations, ZoneRepository zones, UserRepository users,
                                 CurrentUser currentUser, SimpMessagingTemplate messaging) {
        this.recommendations = recommendations; this.zones = zones; this.users = users;
        this.currentUser = currentUser; this.messaging = messaging;
    }

    @Transactional
    public void evaluate(Zone zone, RiskEvent event, RiskLevel previousRisk, RiskEvent previousEvent) {
        List<Zone> venueZones = zones.findByVenueId(zone.getVenue().getId());
        if (event.getRiskLevel().ordinal() < RiskLevel.HIGH.ordinal()) {
            expireZoneRecommendations(zone.getId());
        } else {
            create(zone, RecommendationType.REDIRECT, "Redirect incoming visitors away from " + zone.getName(), event.getRiskLevel());
            create(zone, RecommendationType.DEPLOY_SECURITY, "Deploy additional security personnel to " + zone.getName(), event.getRiskLevel());
        }

        if (event.getRiskLevel() == RiskLevel.CRITICAL) {
            adjacentLowZone(zone, venueZones).ifPresent(adjacent -> create(zone, RecommendationType.OPEN_ROUTE,
                    "Open alternate route via " + adjacent.getName(), RiskLevel.CRITICAL));
            recommendSecurityReassignment(zone, venueZones);
        }

        if (previousEvent != null && !event.getTimestamp().isBefore(previousEvent.getTimestamp())
                && Duration.between(previousEvent.getTimestamp(), event.getTimestamp()).compareTo(RAPID_INCREASE_WINDOW) <= 0
                && event.getDensityScore() - previousEvent.getDensityScore() >= RAPID_DENSITY_INCREASE) {
            create(zone, RecommendationType.CLOSE_ENTRY,
                    "Consider temporarily closing entry to " + zone.getName() + " — density rising rapidly", event.getRiskLevel());
        }

        List<Zone> highRiskZones = venueZones.stream().filter(current -> current.getCurrentRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal()).toList();
        if (highRiskZones.size() >= 2) {
            String names = highRiskZones.stream().map(Zone::getName).sorted().limit(3).reduce((a, b) -> a + ", " + b).orElse("multiple zones");
            create(null, RecommendationType.ANNOUNCEMENT,
                    "Consider venue-wide announcement: \"Please avoid " + names + " and follow staff directions to less crowded routes.\"", highestSeverity(highRiskZones));
        } else {
            recommendations.findByTypeAndStatus(RecommendationType.ANNOUNCEMENT, RecommendationStatus.PENDING)
                    .forEach(recommendation -> {
                        recommendation.setStatus(RecommendationStatus.DISMISSED);
                        broadcast(recommendation);
                    });
        }
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> list(Boolean active) {
        List<Recommendation> result = Boolean.TRUE.equals(active)
                ? recommendations.findByStatusOrderByCreatedAtDesc(RecommendationStatus.PENDING)
                : recommendations.findAllByOrderByCreatedAtDesc();
        return result.stream().map(this::toResponse).toList();
    }
    @Transactional(readOnly = true)
    public List<RecommendationResponse> customerList(Boolean active) {
        List<Recommendation> result = Boolean.TRUE.equals(active)
                ? recommendations.findByStatusOrderByCreatedAtDesc(RecommendationStatus.PENDING)
                : recommendations.findAllByOrderByCreatedAtDesc();
        return result.stream()
                .filter(recommendation -> recommendation.getType() == RecommendationType.OPEN_ROUTE)
                .map(this::toResponse)
                .toList();
    }


    @Transactional
    public RecommendationResponse acknowledge(Long id) {
        Recommendation recommendation = find(id);
        recommendation.setStatus(RecommendationStatus.ACKNOWLEDGED);
        recommendation.setAcknowledgedByUser(currentUser.get());
        return broadcast(recommendation);
    }

    @Transactional
    public RecommendationResponse dismiss(Long id) {
        Recommendation recommendation = find(id);
        recommendation.setStatus(RecommendationStatus.DISMISSED);
        return broadcast(recommendation);
    }

    private void recommendSecurityReassignment(Zone criticalZone, List<Zone> venueZones) {
        long criticalStaff = securityCount(criticalZone.getId());
        venueZones.stream()
                .filter(zone -> zone.getId() != criticalZone.getId())
                .filter(zone -> zone.getCurrentRiskLevel().ordinal() < RiskLevel.CRITICAL.ordinal())
                .max(Comparator.comparingLong(zone -> securityCount(zone.getId())))
                .filter(calmZone -> securityCount(calmZone.getId()) > criticalStaff)
                .ifPresent(calmZone -> create(criticalZone, RecommendationType.REASSIGN_PERSONNEL,
                        "Reassign security personnel from " + calmZone.getName() + " to " + criticalZone.getName(), RiskLevel.CRITICAL));
    }

    private java.util.Optional<Zone> adjacentLowZone(Zone zone, List<Zone> venueZones) {
        return venueZones.stream().filter(candidate -> candidate.getId() != zone.getId())
                .filter(candidate -> candidate.getCurrentRiskLevel() == RiskLevel.LOW)
                .filter(candidate -> distanceMeters(zone, candidate) <= zoneRadius(zone) + zoneRadius(candidate) + 80)
                .min(Comparator.comparingDouble(candidate -> distanceMeters(zone, candidate)));
    }

    private void create(Zone zone, RecommendationType type, String message, RiskLevel severity) {
        Long zoneId = zone == null ? null : zone.getId();
        boolean pending = zoneId == null
                ? recommendations.existsByTypeAndZoneIsNullAndStatus(type, RecommendationStatus.PENDING)
                : recommendations.existsByTypeAndZoneIdAndStatus(type, zoneId, RecommendationStatus.PENDING);
        Instant since = Instant.now().minus(COOLDOWN);
        boolean coolingDown = zoneId == null
                ? recommendations.existsByTypeAndZoneIsNullAndCreatedAtAfter(type, since)
                : recommendations.existsByTypeAndZoneIdAndCreatedAtAfter(type, zoneId, since);
        if (!pending && !coolingDown) broadcast(recommendations.save(new Recommendation(zone, type, message, severity)));
    }

    private void expireZoneRecommendations(Long zoneId) {
        recommendations.findByZoneIdAndStatus(zoneId, RecommendationStatus.PENDING)
                .forEach(recommendation -> {
                    recommendation.setStatus(RecommendationStatus.DISMISSED);
                    broadcast(recommendation);
                });
    }

    private Recommendation find(Long id) { return recommendations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Recommendation", id)); }
    private long securityCount(Long zoneId) { return users.countByAssignedZoneIdAndRoleAndActiveTrue(zoneId, UserRole.SECURITY); }
    private RiskLevel highestSeverity(List<Zone> zones) { return zones.stream().map(Zone::getCurrentRiskLevel).max(Comparator.comparingInt(Enum::ordinal)).orElse(RiskLevel.HIGH); }
    private RecommendationResponse broadcast(Recommendation recommendation) { RecommendationResponse response = toResponse(recommendation); messaging.convertAndSend("/topic/recommendations", response); return response; }
    private RecommendationResponse toResponse(Recommendation recommendation) {
        Zone zone = recommendation.getZone(); User acknowledgedBy = recommendation.getAcknowledgedByUser();
        return new RecommendationResponse(recommendation.getId(), zone == null ? null : zone.getId(), zone == null ? null : zone.getName(),
                recommendation.getType(), recommendation.getMessage(), recommendation.getSeverity(), recommendation.getCreatedAt(),
                recommendation.getStatus(), acknowledgedBy == null ? null : acknowledgedBy.getId());
    }

    private static double zoneRadius(Zone zone) { return zone.getRadiusMeters() == null ? 50 : zone.getRadiusMeters(); }
    private static double distanceMeters(Zone first, Zone second) {
        if (first.getLatitude() == null || first.getLongitude() == null || second.getLatitude() == null || second.getLongitude() == null) return Double.MAX_VALUE;
        double latitudeDelta = Math.toRadians(second.getLatitude() - first.getLatitude());
        double longitudeDelta = Math.toRadians(second.getLongitude() - first.getLongitude());
        double a = Math.pow(Math.sin(latitudeDelta / 2), 2) + Math.cos(Math.toRadians(first.getLatitude())) * Math.cos(Math.toRadians(second.getLatitude())) * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
