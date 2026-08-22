package com.nirikshan.controller;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nirikshan.model.Venue;
import com.nirikshan.repository.VenueRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VenueControllerTest {
    @Test
    void listReturnsFlatVenueSummariesWithoutEntityRelationships() throws Exception {
        VenueRepository venues = mock(VenueRepository.class);
        Venue venue = new Venue("KIIT Campus 25", "Test venue", 20.3641, 85.8163);
        venue.setId(1L);
        venue.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(venues.findAll()).thenReturn(List.of(venue));

        VenueController controller = new VenueController(venues, null, null);
        VenueController.VenueResponse response = controller.list().get(0);
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(response);

        assertEquals(1L, response.id());
        assertEquals("KIIT Campus 25", response.name());
        assertFalse(json.contains("zones"));
    }
}
