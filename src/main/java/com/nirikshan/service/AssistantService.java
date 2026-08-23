package com.nirikshan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirikshan.dto.AssistantChatRequest;
import com.nirikshan.model.*;
import com.nirikshan.repository.AlertRepository;
import com.nirikshan.repository.RecommendationRepository;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.VenueRepository;
import com.nirikshan.repository.ZoneRepository;
import com.nirikshan.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Service
public class AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final String UNAVAILABLE = "I can’t reach the Nirikshan safety service right now. Please try again shortly.";
    private static final int MAX_CONTEXT_CHARACTERS = 10000;
    private static final int MAX_HISTORY_MESSAGES = 8;

    private final ZoneRepository zones;
    private final VenueRepository venues;
    private final RiskEventRepository events;
    private final AlertRepository alerts;
    private final RecommendationRepository recommendations;
    private final CurrentUser currentUser;
    private final GroqChatClient groq;
    private final ObjectMapper mapper;

    public AssistantService(ZoneRepository zones, VenueRepository venues, RiskEventRepository events,
                            AlertRepository alerts, RecommendationRepository recommendations,
                            CurrentUser currentUser,
                            GroqChatClient groq, ObjectMapper mapper) {
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
    public AiLanguage resolveLanguage(AssistantChatRequest request) {
        if (request.language() != null && !request.language().isBlank()) return AiLanguage.fromCode(request.language());
        String message = request.message();
        if (message.chars().anyMatch(character -> character >= 0x0B00 && character <= 0x0B7F)) return AiLanguage.OR;
        if (message.chars().anyMatch(character -> character >= 0x0900 && character <= 0x097F)) return AiLanguage.HI;
        return AiLanguage.EN;
    }

    @Transactional(readOnly = true)
    public String chat(AssistantChatRequest request, AiLanguage language) {
        PreparedChat prepared = prepare(request, language);
        if (prepared.immediateResponse() != null) return prepared.immediateResponse();
        try {
            JsonNode root = mapper.readTree(groq.complete(prepared.system(), prepared.userPrompt(), 1200));
            String response = root.path("choices").path(0).path("message").path("content").asText("").trim();
            return response.isBlank() ? UNAVAILABLE : response;
        } catch (RestClientResponseException failure) {
            log.error("Nirikshan assistant Groq request failed: status={} model={} responseBody={}", failure.getStatusCode().value(), groq.model(), failure.getResponseBodyAsString(), failure);
            return UNAVAILABLE;
        } catch (Exception failure) {
            log.error("Nirikshan assistant request failed: model={} message={}", groq.model(), failure.getMessage(), failure);
            return UNAVAILABLE;
        }
    }

    @Transactional(readOnly = true)
    public void stream(AssistantChatRequest request, AiLanguage language, Consumer<String> onToken, Consumer<String> onReplace,
                       Consumer<Throwable> onError, Runnable onComplete) {
        PreparedChat prepared;
        try {
            prepared = prepare(request, language);
        } catch (Throwable failure) {
            onError.accept(failure);
            return;
        }
        if (prepared.immediateResponse() != null) {
            onToken.accept(prepared.immediateResponse());
            onComplete.run();
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                StringBuilder streamed = new StringBuilder();
                groq.stream(prepared.system(), prepared.userPrompt(), 1200, token -> {
                    streamed.append(token);
                    onToken.accept(token);
                });
                if (!completeSentence(streamed.toString())) {
                    JsonNode root = mapper.readTree(groq.complete(prepared.system(), prepared.userPrompt(), 1200));
                    String complete = root.path("choices").path(0).path("message").path("content").asText("").trim();
                    if (!complete.isBlank()) onReplace.accept(complete);
                }
                onComplete.run();
            } catch (Throwable failure) {
                onError.accept(failure);
            }
        });
    }

    private PreparedChat prepare(AssistantChatRequest request, AiLanguage language) {
        User user = currentUser.get();
        SummaryAudience audience = SummaryAudience.valueOf(user.getRole().name());
        if (!isCampusSafetyQuestion(request.message())) return new PreparedChat(null, null, redirect(language));

        boolean specificZone = request.zoneId() != null;
        List<Zone> scopedZones = resolveZones(request.zoneId(), user, audience);
        String context = buildContext(scopedZones, audience, specificZone);
        String system = systemPrompt(audience, specificZone, language);
        String userPrompt = "Authoritative live Nirikshan context:\n" + context +
                "\n\nConversation so far:\n" + history(request.conversationHistory()) +
                "\n\nLatest user question:\n" + compact(request.message(), 1200);
        if (!groq.isConfigured()) {
            log.warn("Skipping Nirikshan assistant request because GROQ_API_KEY is not configured");
            return new PreparedChat(null, null, UNAVAILABLE);
        }
        return new PreparedChat(system, userPrompt, null);
    }

    private List<Zone> resolveZones(Long requestedZoneId, User user, SummaryAudience audience) {
        if (audience == SummaryAudience.SECURITY) {
            if (user.getAssignedZone() == null) throw new IllegalArgumentException("No security zone is assigned");
            if (requestedZoneId != null && !requestedZoneId.equals(user.getAssignedZone().getId())) {
                throw new IllegalArgumentException("This zone is outside your assignment");
            }
            return List.of(user.getAssignedZone());
        }
        if (requestedZoneId != null) return List.of(zones.findById(requestedZoneId).orElseThrow(() -> new ResourceNotFoundException("Zone", requestedZoneId)));
        Long venueId = user.getAssignedZone() != null ? user.getAssignedZone().getVenue().getId() : venues.findAll().stream().findFirst().map(Venue::getId).orElseThrow(() -> new ResourceNotFoundException("Venue", 0L));
        List<Zone> venueZones = zones.findByVenueId(venueId);
        return audience == SummaryAudience.CITIZEN ? venueZones.stream().limit(5).toList() : venueZones;
    }

    private String buildContext(List<Zone> scopedZones, SummaryAudience audience, boolean specificZone) {
        StringBuilder context = new StringBuilder();
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(10));
        List<Long> zoneIds = scopedZones.stream().map(Zone::getId).toList();
        Map<Long, List<RiskEvent>> recentEvents = specificZone
                ? events.findByZoneIdInAndTimestampAfterOrderByTimestampAsc(zoneIds, cutoff).stream()
                .collect(java.util.stream.Collectors.groupingBy(event -> event.getZone().getId()))
                : Map.of();
        Map<Long, List<Alert>> recentAlerts = alerts.findByZoneIdInAndTimestampAfterOrderByTimestampAsc(zoneIds, cutoff).stream()
                .collect(java.util.stream.Collectors.groupingBy(alert -> alert.getZone().getId()));
        Map<Long, List<Recommendation>> recentRecommendations = recommendations.findByZoneIdInAndCreatedAtAfterOrderByCreatedAtAsc(zoneIds, cutoff).stream()
                .collect(java.util.stream.Collectors.groupingBy(recommendation -> recommendation.getZone().getId()));
        if (!specificZone) {
            long elevated = scopedZones.stream().filter(zone -> zone.getCurrentRiskLevel() != RiskLevel.LOW).count();
            append(context, "CAMPUS-WIDE STATUS: total zones=" + scopedZones.size() + ", normal zones=" + (scopedZones.size() - elevated) + ", elevated zones=" + elevated + "\n");
            scopedZones.stream().filter(zone -> zone.getCurrentRiskLevel() != RiskLevel.LOW).forEach(zone -> append(context, "ELEVATED ZONE: " + zone.getName() + ", risk=" + zone.getCurrentRiskLevel() + ", density=" + zone.getCurrentDensity() + ", people=" + zone.getCurrentPeopleCount() + "\n"));
        }
        for (Zone zone : scopedZones) {
            if (!specificZone) {
                if (zone.getCurrentRiskLevel() != RiskLevel.LOW) {
                    append(context, "ELEVATED DETAIL " + zone.getName() + ": updated=" + zone.getLastUpdated() + "\n");
                }
            } else if (audience == SummaryAudience.CITIZEN) {
                append(context, "ZONE " + zone.getName() + ": current condition=" + zone.getCurrentRiskLevel() + ", last updated=" + zone.getLastUpdated() + "\n");
            } else {
                append(context, "ZONE " + zone.getName() + " (id=" + zone.getId() + "): risk=" + zone.getCurrentRiskLevel() + ", density=" + zone.getCurrentDensity() + ", people=" + zone.getCurrentPeopleCount() + ", updated=" + zone.getLastUpdated() + "\n");
                recentEvents.getOrDefault(zone.getId(), List.of()).stream().sorted(Comparator.comparing(RiskEvent::getTimestamp).reversed()).limit(6).forEach(event -> append(context, "EVENT " + event.getTimestamp() + ": density=" + event.getDensityScore() + ", people=" + event.getPeopleCount() + ", movement=" + event.getMovementSpeed() + ", risk=" + event.getRiskLevel() + ", note=" + compact(event.getExplanation(), 180) + "\n"));
            }
            recentAlerts.getOrDefault(zone.getId(), List.of()).stream().filter(alert -> audience != SummaryAudience.CITIZEN || !alert.isResolved()).limit(5).forEach(alert -> append(context, "ALERT " + zone.getName() + ": severity=" + alert.getSeverity() + ", resolved=" + alert.isResolved() + ", message=" + compact(alert.getMessage(), 220) + "\n"));
            recentRecommendations.getOrDefault(zone.getId(), List.of()).stream().filter(recommendation -> audience != SummaryAudience.CITIZEN || recommendation.getType() == RecommendationType.OPEN_ROUTE).limit(5).forEach(recommendation -> append(context, "RECOMMENDATION " + zone.getName() + ": type=" + recommendation.getType() + ", status=" + recommendation.getStatus() + ", message=" + compact(recommendation.getMessage(), 220) + "\n"));
        }
        return context.length() == 0 ? "No current zone data is available." : context.toString();
    }

    private String systemPrompt(SummaryAudience audience, boolean specificZone, AiLanguage language) {
        String tone = switch (audience) {
            case ADMIN -> "Use a precise, technical operational tone.";
            case SECURITY -> "Use an action-oriented tone focused on what field staff should do next.";
            case CITIZEN -> "Use calm, simple language. Avoid jargon, raw density numbers, movement speed, internal recommendation details, and speculation.";
        };
        String scope = specificZone
                ? "This is a SINGLE-ZONE request. Give a detailed summary for the named zone, using the supplied real numbers, timestamps, alerts, and recommendations where useful."
                : "This is a CAMPUS-WIDE request. Synthesize the overall safety picture in 3 to 5 sentences maximum. Do not enumerate every zone or list raw stats sequentially. Only name zones at MEDIUM, HIGH, or CRITICAL risk, with a brief reason. If every zone is LOW, say that all campus zones are showing normal activity with no elevated risk detected. If any zone is elevated, mention that zone by its actual name and say that all other zones remain normal when applicable.";
        String languageInstruction = "Respond entirely in " + language.displayName() + " (language code " + language.code() + "). Keep proper nouns such as Nirikshan and official zone names unchanged when useful.";
        return "You are the Nirikshan campus safety assistant. " + tone + "\n" + languageInstruction + "\n" + scope + "\n" +
                "You may answer ONLY questions about Nirikshan, campus safety, crowd monitoring, current zone conditions, alerts, incidents, routes, and safety recommendations. " +
                "Politely decline any unrelated question using this idea: you can only help with campus safety and crowd monitoring questions. " +
                "Use only the authoritative context supplied by the application. Never invent numbers, events, locations, alerts, recommendations, or certainty. " +
                "If the context does not contain the answer, say that the current safety data does not show it and suggest checking with campus security. " +
                "Return plain text only. Do not use Markdown, asterisks, hash symbols, or bullet characters. Use short paragraphs and simple sentences. " +
                "The user's access scope is enforced by the application; do not reveal information about zones outside the supplied context.";
    }

    private static String history(List<AssistantChatRequest.HistoryMessage> messages) {
        if (messages == null || messages.isEmpty()) return "No earlier messages.";
        return messages.stream().filter(message -> "user".equals(message.role()) || "assistant".equals(message.role())).skip(Math.max(0, messages.size() - MAX_HISTORY_MESSAGES)).map(message -> message.role() + ": " + compact(message.content(), 600)).reduce((left, right) -> left + "\n" + right).orElse("No earlier messages.");
    }

    private static boolean isCampusSafetyQuestion(String message) {
        String value = message.toLowerCase(Locale.ROOT);
        return List.of(
                "nirikshan", "campus", "safety", "safe", "unsafe", "crowd", "condition", "happening", "summary", "overview", "status", "latest", "zone", "alert", "incident", "risk", "density", "people", "route", "recommendation", "security", "gate", "cafeteria", "hostel", "emergency", "evacuation", "danger",
                "सुरक्षा", "सुरक्षित", "असुरक्षित", "भीड़", "स्थिति", "सारांश", "क्षेत्र", "जोन", "अलर्ट", "घटना", "जोखिम", "घनत्व", "लोग", "मार्ग", "सिफारिश", "सुरक्षा", "आपातकाल", "खतरा", "कैंपस", "कैम्पस",
                "ସୁରକ୍ଷା", "ନିରାପଦ", "ଅସୁରକ୍ଷିତ", "ଭିଡ଼", "ସ୍ଥିତି", "ସାରାଂଶ", "ଜୋନ", "ସତର୍କ", "ଘଟଣା", "ବିପଦ", "ଘନତା", "ଲୋକ", "ମାର୍ଗ", "ସୁପାରିଶ", "ଜରୁରୀ", "କ୍ୟାମ୍ପସ"
        ).stream().anyMatch(value::contains);
    }

    private static String redirect(AiLanguage language) {
        return switch (language) {
            case HI -> "मैं केवल कैंपस सुरक्षा और भीड़ निगरानी से जुड़े सवालों में मदद कर सकता हूँ। क्या आप वर्तमान स्थितियों के बारे में कुछ जानना चाहते हैं?";
            case OR -> "ମୁଁ କେବଳ କ୍ୟାମ୍ପସ ସୁରକ୍ଷା ଏବଂ ଭିଡ଼ ନିରୀକ୍ଷଣ ସମ୍ବନ୍ଧୀୟ ପ୍ରଶ୍ନରେ ସାହାଯ୍ୟ କରିପାରିବି। ବର୍ତ୍ତମାନ ସ୍ଥିତି ବିଷୟରେ କିଛି ଜାଣିବାକୁ ଚାହୁଁଛନ୍ତି କି?";
            case EN -> "I can only help with campus safety and crowd monitoring questions. Is there something about current conditions I can help with?";
        };
    }

    private static String compact(String value, int limit) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() > limit ? normalized.substring(0, limit) + "..." : normalized;
    }

    private static boolean completeSentence(String value) {
        String text = value == null ? "" : value.trim();
        return !text.isBlank() && text.matches(".*[.!?؟।]$");
    }

    private static void append(StringBuilder value, String addition) {
        if (value.length() >= MAX_CONTEXT_CHARACTERS) return;
        int remaining = MAX_CONTEXT_CHARACTERS - value.length();
        value.append(addition, 0, Math.min(addition.length(), remaining));
    }

    private record PreparedChat(String system, String userPrompt, String immediateResponse) { }
}
