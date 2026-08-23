package com.nirikshan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirikshan.dto.AssistantChatRequest;
import com.nirikshan.model.RiskEvent;
import com.nirikshan.model.RiskEventSource;
import com.nirikshan.model.User;
import com.nirikshan.model.UserRole;
import com.nirikshan.model.Venue;
import com.nirikshan.model.Zone;
import com.nirikshan.repository.AlertRepository;
import com.nirikshan.repository.RecommendationRepository;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.VenueRepository;
import com.nirikshan.repository.ZoneRepository;
import com.nirikshan.security.CurrentUser;
import com.nirikshan.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTest {
    @Test
    void singleZoneChatIncludesBatchedTelemetryAndResponse() {
        ZoneRepository zones = mock(ZoneRepository.class);
        VenueRepository venues = mock(VenueRepository.class);
        RiskEventRepository events = mock(RiskEventRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);
        RecommendationRepository recommendations = mock(RecommendationRepository.class);
        Zone zone = zone(1L, "Main Gate");
        User user = user();
        CurrentUser currentUser = new TestCurrentUser(user);
        GroqChatClient groq = new TestGroqChatClient();
        when(zones.findById(1L)).thenReturn(Optional.of(zone));
        when(events.findByZoneIdInAndTimestampAfterOrderByTimestampAsc(anyList(), any())).thenReturn(List.of(event(zone)));
        when(alerts.findByZoneIdInAndTimestampAfterOrderByTimestampAsc(anyList(), any())).thenReturn(List.of());
        when(recommendations.findByZoneIdInAndCreatedAtAfterOrderByCreatedAtAsc(anyList(), any())).thenReturn(List.of());
        AssistantService service = new AssistantService(zones, venues, events, alerts, recommendations, currentUser, groq, new ObjectMapper());

        assertEquals("response", service.chat(new AssistantChatRequest("What is the current crowd condition?", 1L, "en", List.of()), com.nirikshan.model.AiLanguage.EN));
        verify(events).findByZoneIdInAndTimestampAfterOrderByTimestampAsc(anyList(), any());
    }

    @Test
    void campusWideChatUsesOneQueryPerDataTypeAndReturnsResponse() {
        ZoneRepository zones = mock(ZoneRepository.class);
        VenueRepository venues = mock(VenueRepository.class);
        RiskEventRepository events = mock(RiskEventRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);
        RecommendationRepository recommendations = mock(RecommendationRepository.class);
        Venue venue = new Venue();
        venue.setId(1L);
        Zone first = zone(1L, "Main Gate"); first.setVenue(venue);
        Zone second = zone(2L, "Cafeteria"); second.setVenue(venue);
        User user = user();
        CurrentUser currentUser = new TestCurrentUser(user);
        GroqChatClient groq = new TestGroqChatClient();
        when(venues.findAll()).thenReturn(List.of(venue));
        when(zones.findByVenueId(any())).thenReturn(List.of(first, second));
        when(alerts.findByZoneIdInAndTimestampAfterOrderByTimestampAsc(anyList(), any())).thenReturn(List.of());
        when(recommendations.findByZoneIdInAndCreatedAtAfterOrderByCreatedAtAsc(anyList(), any())).thenReturn(List.of());
        AssistantService service = new AssistantService(zones, venues, events, alerts, recommendations, currentUser, groq, new ObjectMapper());

        assertEquals("response", service.chat(new AssistantChatRequest("Give me the current campus safety status.", null, "en", List.of()), com.nirikshan.model.AiLanguage.EN));
        verify(alerts).findByZoneIdInAndTimestampAfterOrderByTimestampAsc(anyList(), any());
        verify(recommendations).findByZoneIdInAndCreatedAtAfterOrderByCreatedAtAsc(anyList(), any());
    }

    private static User user() {
        User user = new User();
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private static Zone zone(Long id, String name) {
        Zone zone = new Zone();
        zone.setId(id);
        zone.setName(name);
        zone.setLastUpdated(Instant.now());
        return zone;
    }

    private static RiskEvent event(Zone zone) {
        RiskEvent event = new RiskEvent();
        event.setZone(zone);
        event.setTimestamp(Instant.now());
        event.setDensityScore(2.4);
        event.setPeopleCount(42);
        event.setMovementSpeed(1.1);
        event.setRiskLevel(com.nirikshan.model.RiskLevel.MEDIUM);
        event.setSource(RiskEventSource.LIVE);
        event.setExplanation("Elevated crowd density");
        return event;
    }

    private static class TestCurrentUser extends CurrentUser {
        private final User user;
        TestCurrentUser(User user) { super(mock(UserRepository.class)); this.user = user; }
        @Override public User get() { return user; }
    }

    private static class TestGroqChatClient extends GroqChatClient {
        TestGroqChatClient() { super("", "llama-3.1-8b-instant", new ObjectMapper()); }
        @Override public boolean isConfigured() { return true; }
        @Override public String complete(String systemPrompt, String userPrompt, int maxCompletionTokens) {
            return "{\"choices\":[{\"message\":{\"content\":\"response\"}}]}";
        }
    }
}
