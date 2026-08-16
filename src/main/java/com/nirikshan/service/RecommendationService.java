package com.nirikshan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirikshan.dto.RecommendationResponse;
import com.nirikshan.dto.RiskForecastResponse;
import com.nirikshan.model.*;
import com.nirikshan.repository.RecommendationRepository;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.UserRepository;
import com.nirikshan.repository.ZoneRepository;
import com.nirikshan.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final Duration ANALYSIS_WINDOW = Duration.ofSeconds(60);
    private static final Duration MIN_SUSTAINED_SPAN = Duration.ofSeconds(20);
    private static final Duration RECOVERY_WINDOW = Duration.ofSeconds(20);
    private static final Duration RECOMMENDATION_COOLDOWN = Duration.ofMinutes(2);
    private static final Duration AI_RETRY_INTERVAL = Duration.ofSeconds(15);
    private static final double MEDIUM_PLUS_RATIO = 0.65;
    private static final double HIGH_PLUS_RATIO = 0.40;
    private static final double RISING_DENSITY_DELTA = 0.35;

    private final RecommendationRepository recommendations;
    private final RiskEventRepository events;
    private final ZoneRepository zones;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final SimpMessagingTemplate messaging;
    private final GroqChatClient groq;
    private final ObjectMapper mapper;
    private final RiskForecastService forecastService;
    private final Map<Long, Instant> lastPatternAnalysis = new ConcurrentHashMap<>();

    public RecommendationService(RecommendationRepository recommendations, RiskEventRepository events,
                                 ZoneRepository zones, UserRepository users, CurrentUser currentUser,
                                 SimpMessagingTemplate messaging, GroqChatClient groq, ObjectMapper mapper,
                                 RiskForecastService forecastService) {
        this.recommendations = recommendations;
        this.events = events;
        this.zones = zones;
        this.users = users;
        this.currentUser = currentUser;
        this.messaging = messaging;
        this.groq = groq;
        this.mapper = mapper;
        this.forecastService = forecastService;
    }

    @Transactional
    public void evaluate(Zone zone, RiskEvent event, RiskLevel previousRisk, RiskEvent previousEvent) {
        List<RiskEvent> window = recentWindow(zone.getId(), event.getTimestamp());

        if (isGenuinelyRecovered(window)) {
            lastPatternAnalysis.remove(zone.getId());
            expireZoneRecommendations(zone.getId());
        } else if (!hasPendingZoneRecommendation(zone.getId()) && isSustainedCandidate(window)
                && shouldAnalyze(zone.getId(), event.getTimestamp())) {
            lastPatternAnalysis.put(zone.getId(), event.getTimestamp());
            PatternAssessment assessment = assessPattern(zone, window);
            if (assessment.sustained()) {
                create(zone, assessment.type(), assessment.message(), assessment.severity(), event.getSource());
            }
        }

        RiskForecastResponse forecast = forecastService.forecast(zone.getId());
        createProjectedRecommendations(zone, forecast, event.getSource());

        List<Zone> venueZones = zones.findByVenueId(zone.getVenue().getId());
        List<RiskForecastResponse> projectedHighZones = venueZones.stream()
                .map(current -> forecastService.forecast(current.getId()))
                .filter(current -> !current.stale() && current.projectedRisk().ordinal() >= RiskLevel.HIGH.ordinal())
                .toList();
        if (RecommendationRuleEngine.venueAnnouncementRequired(projectedHighZones.size())) {
            String names = projectedHighZones.stream().map(RiskForecastResponse::zoneName).sorted().limit(3)
                    .reduce((a, b) -> a + ", " + b).orElse("multiple zones");
            create(null, RecommendationType.ANNOUNCEMENT,
                    "Projected HIGH or CRITICAL risk across " + names + "; direct visitors to less crowded routes and follow staff instructions.",
                    projectedHighZones.stream().map(RiskForecastResponse::projectedRisk).max(Comparator.comparingInt(Enum::ordinal)).orElse(RiskLevel.HIGH), event.getSource());
        }
    }

    private void createProjectedRecommendations(Zone zone, RiskForecastResponse forecast, RiskEventSource source) {
        if (forecast.stale() || forecast.state() == RiskForecastState.INSUFFICIENT_DATA
                || forecast.projectedRisk().ordinal() <= forecast.currentRisk().ordinal()) return;
        String prefix = "Projected " + forecast.projectedRisk() + " risk, not a confirmed current incident: ";
        if (forecast.projectedRisk() == RiskLevel.CRITICAL) {
            // A zone has one current pending action. CLOSE_ENTRY is the primary
            // containment action; security coordination belongs in its handoff
            // message instead of creating three competing queue items.
            create(zone, RecommendationType.CLOSE_ENTRY,
                    prefix + "prepare to close entry and coordinate security because the next threshold may be reached in "
                            + forecast.estimatedSecondsToProjectedRisk() + " seconds.",
                    RiskLevel.CRITICAL, source);
        } else if (forecast.projectedRisk() == RiskLevel.HIGH) {
            create(zone, RecommendationType.REDIRECT, prefix + "redirect incoming visitors before density reaches the next threshold.", RiskLevel.HIGH, source);
        } else if (forecast.projectedRisk() == RiskLevel.MEDIUM) {
            create(zone, RecommendationType.OPEN_ROUTE, prefix + "open an alternate route while crowding is still manageable.", RiskLevel.MEDIUM, source);
        }
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> list(Boolean active) {
        List<Recommendation> result = Boolean.TRUE.equals(active)
                ? uniquePending(recommendations.findByStatusOrderByCreatedAtDesc(RecommendationStatus.PENDING))
                : recommendations.findAllByOrderByCreatedAtDesc();
        return result.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> customerList(Boolean active) {
        List<Recommendation> result = Boolean.TRUE.equals(active)
                ? uniquePending(recommendations.findByStatusOrderByCreatedAtDesc(RecommendationStatus.PENDING))
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

    @Transactional
    public void clearZoneState(Long zoneId) {
        expireZoneRecommendations(zoneId);
    }

    private List<RiskEvent> recentWindow(Long zoneId, Instant end) {
        Instant cutoff = end.minus(ANALYSIS_WINDOW);
        return events.findByZoneIdOrderByTimestampDesc(zoneId, PageRequest.of(0, 50)).stream()
                .filter(event -> !event.getTimestamp().isBefore(cutoff) && !event.getTimestamp().isAfter(end))
                .sorted(Comparator.comparing(RiskEvent::getTimestamp))
                .toList();
    }

    private boolean isSustainedCandidate(List<RiskEvent> window) {
        if (window.size() < 3) return false;
        Duration span = Duration.between(window.get(0).getTimestamp(), window.get(window.size() - 1).getTimestamp());
        if (span.compareTo(MIN_SUSTAINED_SPAN) < 0) return false;
        long mediumPlus = window.stream().filter(event -> event.getRiskLevel().ordinal() >= RiskLevel.MEDIUM.ordinal()).count();
        long highPlus = window.stream().filter(event -> event.getRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal()).count();
        double densityDelta = window.get(window.size() - 1).getDensityScore() - window.get(0).getDensityScore();
        return mediumPlus / (double) window.size() >= MEDIUM_PLUS_RATIO
                || highPlus / (double) window.size() >= HIGH_PLUS_RATIO
                || (densityDelta >= RISING_DENSITY_DELTA && risingReadings(window) >= Math.max(2, window.size() / 2));
    }

    private boolean isGenuinelyRecovered(List<RiskEvent> window) {
        if (window.size() < 3) return false;
        Duration span = Duration.between(window.get(0).getTimestamp(), window.get(window.size() - 1).getTimestamp());
        return span.compareTo(RECOVERY_WINDOW) >= 0
                && window.stream().allMatch(event -> event.getRiskLevel() == RiskLevel.LOW);
    }

    private PatternAssessment assessPattern(Zone zone, List<RiskEvent> window) {
        PatternMetrics metrics = metrics(window);
        PatternAssessment fallback = fallbackAssessment(zone, metrics);
        // Deterministic local rules choose the action and severity. AI is not
        // consulted for core detection or action selection.
        /* unreachable legacy AI wording path retained for future opt-in */
        if (false) {
        String prompt = "Analyze this Nirikshan risk-event rolling window for zone '" + zone.getName() + "'. "
                + "Decide whether the concerning pattern is sustained rather than a momentary spike. "
                + "Use only the supplied observations. Return JSON only with exactly these fields: "
                + "sustained (boolean), recommendationType (one of REDIRECT, DEPLOY_SECURITY, OPEN_ROUTE, CLOSE_ENTRY, ANNOUNCEMENT, REASSIGN_PERSONNEL), "
                + "severity (one of MEDIUM, HIGH, CRITICAL), explanation (brief plain text). "
                + "A sustained pattern requires at least 65% MEDIUM+ readings or a clear rising density trend over the window. "
                + "If the data is noisy or insufficient, set sustained=false.\n\n"
                + "Window metrics: readings=" + metrics.readings() + ", spanSeconds=" + metrics.spanSeconds()
                + ", mediumPlusRatio=" + format(metrics.mediumPlusRatio()) + ", highPlusRatio=" + format(metrics.highPlusRatio())
                + ", densityStart=" + format(metrics.densityStart()) + ", densityEnd=" + format(metrics.densityEnd())
                + ", densityDelta=" + format(metrics.densityDelta()) + ", risingReadings=" + metrics.risingReadings() + "\n"
                + eventSeries(window);
        try {
            JsonNode root = mapper.readTree(groq.complete(
                    "You are the Nirikshan recommendation stability analyst. Never invent data. Return valid JSON only.",
                    prompt, 500));
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            JsonNode assessment = mapper.readTree(stripJsonFences(content));
            boolean sustained = assessment.path("sustained").asBoolean(false);
            RecommendationType type = parseType(assessment.path("recommendationType").asText(), fallback.type());
            RiskLevel severity = parseSeverity(assessment.path("severity").asText(), fallback.severity());
            String explanation = assessment.path("explanation").asText("").trim();
            if (explanation.isBlank()) return fallback;
            return new PatternAssessment(sustained, type, severity,
                    zone.getName() + " — " + compact(explanation, 480));
        } catch (Exception failure) {
            log.warn("Recommendation pattern analysis fell back to local metrics for zone {}: {}", zone.getId(), failure.getMessage());
            return fallback;
        }
        }
        return fallback;
    }

    private PatternAssessment fallbackAssessment(Zone zone, PatternMetrics metrics) {
        RiskLevel severity = metrics.highPlusRatio() >= HIGH_PLUS_RATIO ? RiskLevel.HIGH : RiskLevel.MEDIUM;
        boolean bottleneck = metrics.hotspotReadings() >= Math.max(3, metrics.readings() * 0.6) && metrics.averageSlowdown() >= 0.20;
        RecommendationRuleEngine.Decision decision = RecommendationRuleEngine.choose(severity, metrics.densityDelta(), metrics.averageSlowdown(), bottleneck, securityCount(zone.getId()) == 0);
        RecommendationType type = decision.type();
        String direction = switch (type) {
            case CLOSE_ENTRY -> "with a persistent hotspot and movement slowdown";
            case REDIRECT -> "and density is rising";
            case DEPLOY_SECURITY -> "with movement slowing";
            case REASSIGN_PERSONNEL -> "with no active security assignment";
            default -> "with no sustained dispersal";
        };
        String message = zone.getName() + " — density stayed at MEDIUM+ for " + Math.round(metrics.mediumPlusRatio() * 100)
                + "% of the last " + metrics.spanSeconds() + " seconds " + direction + ".";
        return new PatternAssessment(true, type, severity, message);
    }

    private PatternMetrics metrics(List<RiskEvent> window) {
        RiskEvent first = window.get(0);
        RiskEvent last = window.get(window.size() - 1);
        long mediumPlus = window.stream().filter(event -> event.getRiskLevel().ordinal() >= RiskLevel.MEDIUM.ordinal()).count();
        long highPlus = window.stream().filter(event -> event.getRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal()).count();
        return new PatternMetrics(window.size(), Duration.between(first.getTimestamp(), last.getTimestamp()).toSeconds(),
                mediumPlus / (double) window.size(), highPlus / (double) window.size(), first.getDensityScore(),
                last.getDensityScore(), last.getDensityScore() - first.getDensityScore(), risingReadings(window),
                window.stream().mapToDouble(RiskEvent::getMovementSlowdown).average().orElse(0),
                window.stream().filter(event -> event.getHotspotRegions() != null && !event.getHotspotRegions().isBlank()).count());
    }

    private int risingReadings(List<RiskEvent> window) {
        int rising = 0;
        for (int index = 1; index < window.size(); index++) {
            if (window.get(index).getDensityScore() >= window.get(index - 1).getDensityScore()) rising++;
        }
        return rising;
    }

    private String eventSeries(List<RiskEvent> window) {
        StringBuilder series = new StringBuilder("Observations:\n");
        window.forEach(event -> series.append(event.getTimestamp()).append(" density=")
                .append(format(event.getDensityScore())).append(" risk=").append(event.getRiskLevel())
                .append(" movement=").append(format(event.getMovementSpeed())).append("\n"));
        return series.toString();
    }

    private boolean hasPendingZoneRecommendation(Long zoneId) {
        return recommendations.findByZoneIdAndStatus(zoneId, RecommendationStatus.PENDING).stream().findAny().isPresent();
    }

    private boolean shouldAnalyze(Long zoneId, Instant eventTime) {
        Instant last = lastPatternAnalysis.get(zoneId);
        return last == null || eventTime.isBefore(last) || Duration.between(last, eventTime).compareTo(AI_RETRY_INTERVAL) >= 0;
    }

    private void create(Zone zone, RecommendationType type, String message, RiskLevel severity, RiskEventSource source) {
        Long zoneId = zone == null ? null : zone.getId();
        List<Recommendation> pendingForZone = zoneId == null
                ? recommendations.findByStatusOrderByCreatedAtDesc(RecommendationStatus.PENDING).stream()
                    .filter(existing -> existing.getZone() == null && existing.getType() == type).toList()
                : recommendations.findByZoneIdAndStatus(zoneId, RecommendationStatus.PENDING);
        Recommendation sameType = pendingForZone.stream().filter(existing -> existing.getType() == type).findFirst().orElse(null);
        if (sameType != null) {
            // Refresh the existing item in place so repeated telemetry updates
            // the explanation without creating a duplicate queue entry.
            sameType.setMessage(message);
            sameType.setSeverity(severity);
            sameType.setSource(source);
            broadcast(sameType);
            pendingForZone.stream().filter(existing -> existing != sameType).forEach(this::dismissPending);
            return;
        }
        if (zoneId != null) {
            int strongestPending = pendingForZone.stream().mapToInt(existing -> actionPriority(existing.getType())).max().orElse(0);
            if (strongestPending >= actionPriority(type)) return;
            pendingForZone.forEach(this::dismissPending);
        }
        Instant since = Instant.now().minus(RECOMMENDATION_COOLDOWN);
        boolean coolingDown = zoneId == null
                ? recommendations.existsByTypeAndZoneIsNullAndCreatedAtAfter(type, since)
                : recommendations.existsByTypeAndZoneIdAndCreatedAtAfter(type, zoneId, since);
        if (!coolingDown) broadcast(recommendations.save(new Recommendation(zone, type, message, severity, source)));
    }

    private void dismissPending(Recommendation recommendation) {
        if (recommendation.getStatus() != RecommendationStatus.PENDING) return;
        recommendation.setStatus(RecommendationStatus.DISMISSED);
        broadcast(recommendation);
    }

    private int actionPriority(RecommendationType type) {
        return switch (type) {
            case CLOSE_ENTRY -> 5;
            case DEPLOY_SECURITY, REASSIGN_PERSONNEL -> 4;
            case REDIRECT -> 3;
            case OPEN_ROUTE -> 2;
            case ANNOUNCEMENT -> 1;
        };
    }

    private void expireZoneRecommendations(Long zoneId) {
        recommendations.findByZoneIdAndStatus(zoneId, RecommendationStatus.PENDING).forEach(recommendation -> {
            recommendation.setStatus(RecommendationStatus.DISMISSED);
            broadcast(recommendation);
        });
    }

    private List<Recommendation> uniquePending(List<Recommendation> pending) {
        Map<String, Recommendation> byScope = new LinkedHashMap<>();
        pending.forEach(item -> byScope.putIfAbsent(item.getZone() == null ? "venue" : "zone:" + item.getZone().getId(), item));
        return List.copyOf(byScope.values());
    }

    private Recommendation find(Long id) { return recommendations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Recommendation", id)); }
    private long securityCount(Long zoneId) { return users.countByAssignedZoneIdAndRoleAndActiveTrue(zoneId, UserRole.SECURITY); }
    private RiskLevel highestSeverity(List<Zone> zoneList) { return zoneList.stream().map(Zone::getCurrentRiskLevel).max(Comparator.comparingInt(Enum::ordinal)).orElse(RiskLevel.HIGH); }
    private RecommendationResponse broadcast(Recommendation recommendation) { RecommendationResponse response = toResponse(recommendation); messaging.convertAndSend("/topic/recommendations", response); return response; }
    private RecommendationResponse toResponse(Recommendation recommendation) {
        Zone zone = recommendation.getZone(); User acknowledgedBy = recommendation.getAcknowledgedByUser();
        return new RecommendationResponse(recommendation.getId(), zone == null ? null : zone.getId(), zone == null ? null : zone.getName(),
                recommendation.getType(), recommendation.getMessage(), recommendation.getSeverity(), recommendation.getCreatedAt(),
                recommendation.getStatus(), acknowledgedBy == null ? null : acknowledgedBy.getId(), recommendation.getSource());
    }

    private static RecommendationType parseType(String value, RecommendationType fallback) {
        try { return RecommendationType.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return fallback; }
    }
    private static RiskLevel parseSeverity(String value, RiskLevel fallback) {
        try { return RiskLevel.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return fallback; }
    }
    private static String stripJsonFences(String value) { return value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim(); }
    private static String compact(String value, int limit) { String normalized = value.replaceAll("\\s+", " ").trim(); return normalized.length() > limit ? normalized.substring(0, limit) + "..." : normalized; }
    private static String format(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    private record PatternMetrics(int readings, long spanSeconds, double mediumPlusRatio, double highPlusRatio,
                                  double densityStart, double densityEnd, double densityDelta, int risingReadings,
                                  double averageSlowdown, long hotspotReadings) { }
    private record PatternAssessment(boolean sustained, RecommendationType type, RiskLevel severity, String message) { }
}
