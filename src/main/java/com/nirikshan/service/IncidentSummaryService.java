package com.nirikshan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirikshan.dto.IncidentSummaryResponse;
import com.nirikshan.model.*;
import com.nirikshan.repository.AlertRepository;
import com.nirikshan.repository.RecommendationRepository;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.VenueRepository;
import com.nirikshan.repository.ZoneRepository;
import com.nirikshan.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class IncidentSummaryService {
    private static final Logger log = LoggerFactory.getLogger(IncidentSummaryService.class);
    private static final int MAX_CONTEXT_CHARACTERS = 10000;

    private final ZoneRepository zones;
    private final VenueRepository venues;
    private final RiskEventRepository events;
    private final AlertRepository alerts;
    private final RecommendationRepository recommendations;
    private final CurrentUser currentUser;
    private final GroqChatClient groq;
    private final ObjectMapper mapper;

    public IncidentSummaryService(ZoneRepository zones, VenueRepository venues, RiskEventRepository events,
                                  AlertRepository alerts, RecommendationRepository recommendations,
                                  CurrentUser currentUser, GroqChatClient groq, ObjectMapper mapper) {
        this.zones = zones;
        this.venues = venues;
        this.events = events;
        this.alerts = alerts;
        this.recommendations = recommendations;
        this.currentUser = currentUser;
        this.groq = groq;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public IncidentSummaryResponse forZone(Long zoneId, String languageCode) {
        AiLanguage language = AiLanguage.fromCode(languageCode);
        User user = currentUser.get();
        Zone zone = zones.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("Zone", zoneId));
        enforceZoneAccess(user, zone);
        String context = zoneContext(zone, user.getRole() == UserRole.CITIZEN);
        String prompt = summaryPrompt(SummaryAudience.valueOf(user.getRole().name()), language, true) +
                "\n\nAuthoritative live context:\n" + context;
        return response(generate(prompt, language, "zone"), language, "zone", zone.getId(), zone.getName());
    }

    @Transactional(readOnly = true)
    public IncidentSummaryResponse forVenue(String languageCode) {
        AiLanguage language = AiLanguage.fromCode(languageCode);
        User user = currentUser.get();
        List<Zone> scopedZones = venueZones(user);
        SummaryAudience audience = SummaryAudience.valueOf(user.getRole().name());
        String context = venueContext(scopedZones, audience);
        String prompt = summaryPrompt(audience, language, false) +
                "\n\nAuthoritative live context:\n" + context;
        Long venueId = scopedZones.isEmpty() ? null : scopedZones.get(0).getVenue().getId();
        String venueName = scopedZones.isEmpty() ? null : scopedZones.get(0).getVenue().getName();
        return response(generate(prompt, language, "venue"), language, "venue", venueId, venueName);
    }

    private void enforceZoneAccess(User user, Zone zone) {
        if (user.getRole() == UserRole.SECURITY && (user.getAssignedZone() == null || !user.getAssignedZone().getId().equals(zone.getId()))) {
            throw new IllegalArgumentException("This zone is outside your assignment");
        }
    }

    private List<Zone> venueZones(User user) {
        if (user.getAssignedZone() != null) {
            Long venueId = user.getAssignedZone().getVenue().getId();
            if (user.getRole() == UserRole.SECURITY) return List.of(user.getAssignedZone());
            return zones.findByVenueId(venueId).stream().limit(user.getRole() == UserRole.CITIZEN ? 5 : Integer.MAX_VALUE).toList();
        }
        Long venueId = venues.findAll().stream().findFirst().map(Venue::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Venue", 0L));
        return zones.findByVenueId(venueId).stream().limit(user.getRole() == UserRole.CITIZEN ? 5 : Integer.MAX_VALUE).toList();
    }

    private String zoneContext(Zone zone, boolean citizen) {
        StringBuilder context = new StringBuilder();
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(10));
        append(context, "ZONE " + zone.getName() + ": risk=" + zone.getCurrentRiskLevel() + ", density=" + zone.getCurrentDensity() +
                ", people=" + zone.getCurrentPeopleCount() + ", updated=" + zone.getLastUpdated() + "\n");
        if (!citizen) {
            events.findByZoneIdOrderByTimestampDesc(zone.getId(), PageRequest.of(0, 6)).stream()
                    .filter(event -> event.getTimestamp().isAfter(cutoff))
                    .forEach(event -> append(context, "EVENT: " + event.getTimestamp() + ", density=" + event.getDensityScore() +
                            ", people=" + event.getPeopleCount() + ", movement=" + event.getMovementSpeed() +
                            ", risk=" + event.getRiskLevel() + ", note=" + compact(event.getExplanation(), 180) + "\n"));
        }
        alerts.findByZoneIdAndTimestampAfterOrderByTimestampAsc(zone.getId(), cutoff).stream()
                .filter(alert -> !citizen || !alert.isResolved()).limit(5)
                .forEach(alert -> append(context, "ALERT: severity=" + alert.getSeverity() + ", resolved=" + alert.isResolved() +
                        ", message=" + compact(alert.getMessage(), 220) + "\n"));
        recommendations.findByZoneIdAndCreatedAtAfterOrderByCreatedAtAsc(zone.getId(), cutoff).stream()
                .filter(recommendation -> !citizen || recommendation.getType() == RecommendationType.OPEN_ROUTE).limit(5)
                .forEach(recommendation -> append(context, "RECOMMENDATION: type=" + recommendation.getType() +
                        ", status=" + recommendation.getStatus() + ", message=" + compact(recommendation.getMessage(), 220) + "\n"));
        return context.toString();
    }

    private String venueContext(List<Zone> scopedZones, SummaryAudience audience) {
        StringBuilder context = new StringBuilder();
        long elevated = scopedZones.stream().filter(zone -> zone.getCurrentRiskLevel() != RiskLevel.LOW).count();
        append(context, "CAMPUS STATUS: total zones=" + scopedZones.size() + ", normal zones=" + (scopedZones.size() - elevated) +
                ", elevated zones=" + elevated + "\n");
        scopedZones.stream().filter(zone -> zone.getCurrentRiskLevel() != RiskLevel.LOW).forEach(zone ->
                append(context, "ELEVATED ZONE: " + zone.getName() + ", risk=" + zone.getCurrentRiskLevel() +
                        ", density=" + zone.getCurrentDensity() + ", people=" + zone.getCurrentPeopleCount() + "\n"));
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(10));
        scopedZones.forEach(zone -> {
            alerts.findByZoneIdAndTimestampAfterOrderByTimestampAsc(zone.getId(), cutoff).stream()
                    .filter(alert -> audience != SummaryAudience.CITIZEN || !alert.isResolved()).limit(3)
                    .forEach(alert -> append(context, "ALERT " + zone.getName() + ": severity=" + alert.getSeverity() +
                            ", message=" + compact(alert.getMessage(), 180) + "\n"));
            recommendations.findByZoneIdAndCreatedAtAfterOrderByCreatedAtAsc(zone.getId(), cutoff).stream()
                    .filter(recommendation -> audience != SummaryAudience.CITIZEN || recommendation.getType() == RecommendationType.OPEN_ROUTE).limit(3)
                    .forEach(recommendation -> append(context, "RECOMMENDATION " + zone.getName() + ": type=" + recommendation.getType() +
                            ", message=" + compact(recommendation.getMessage(), 180) + "\n"));
        });
        return context.length() == 0 ? "No current zone data is available." : context.toString();
    }

    private String summaryPrompt(SummaryAudience audience, AiLanguage language, boolean specificZone) {
        String tone = switch (audience) {
            case ADMIN -> "Use a precise, technical operational tone.";
            case SECURITY -> "Use an action-oriented tone focused on what field staff should do next.";
            case CITIZEN -> "Use calm, simple language and avoid jargon or internal details.";
        };
        String scope = specificZone
                ? "This is a single-zone incident summary. Give useful detail for that named zone, including real numbers, timestamps, alerts, and recommendations when supplied."
                : "This is a campus-wide incident summary. Synthesize the overall safety picture in 3 to 5 sentences maximum. Do not enumerate every zone or dump raw stats. Name only elevated zones and briefly explain why; if all zones are LOW, say all zones are normal; mention all other zones collectively.";
        return "You are the Nirikshan incident summary writer. " + tone + "\n" +
                "Write entirely in " + language.displayName() + " (" + language.code() + "). Keep official zone names and Nirikshan unchanged when useful.\n" + scope + "\n" +
                "Use only the authoritative live context. Never invent numbers, incidents, alerts, recommendations, or certainty. If data is missing, say so plainly. Return plain text only without Markdown, asterisks, hash symbols, or bullet characters.";
    }

    private String generate(String system, AiLanguage language, String scope) {
        if (!groq.isConfigured()) {
            log.warn("Skipping {} incident summary because GROQ_API_KEY is not configured", scope);
            return unavailable(language);
        }
        try {
            JsonNode root = mapper.readTree(groq.complete(system, "Generate the requested safety summary now.", 700));
            String response = root.path("choices").path(0).path("message").path("content").asText("").trim();
            return response.isBlank() ? unavailable(language) : response;
        } catch (RestClientResponseException failure) {
            log.error("Nirikshan {} incident summary Groq request failed: status={} model={} responseBody={}", scope,
                    failure.getStatusCode().value(), groq.model(), failure.getResponseBodyAsString(), failure);
            return unavailable(language);
        } catch (Exception failure) {
            log.error("Nirikshan {} incident summary request failed: model={} message={}", scope, groq.model(), failure.getMessage(), failure);
            return unavailable(language);
        }
    }

    private IncidentSummaryResponse response(String summary, AiLanguage language, String scope, Long zoneId, String zoneName) {
        return new IncidentSummaryResponse(summary, language.code(), scope, zoneId, zoneName, Instant.now());
    }

    private static String unavailable(AiLanguage language) {
        return switch (language) {
            case HI -> "सारांश अभी उपलब्ध नहीं है। कृपया थोड़ी देर बाद फिर प्रयास करें।";
            case OR -> "ସାରାଂଶ ବର୍ତ୍ତମାନ ଉପଲବ୍ଧ ନାହିଁ। ଦୟାକରି କିଛି ସମୟ ପରେ ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ।";
            case EN -> "Summary unavailable right now. Please try again shortly.";
        };
    }

    private static String compact(String value, int limit) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() > limit ? normalized.substring(0, limit) + "..." : normalized;
    }

    private static void append(StringBuilder value, String addition) {
        if (value.length() >= MAX_CONTEXT_CHARACTERS) return;
        int remaining = MAX_CONTEXT_CHARACTERS - value.length();
        value.append(addition, 0, Math.min(addition.length(), remaining));
    }
}
