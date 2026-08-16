package com.nirikshan.dto;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nirikshan.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskEventResponseSerializationTest {
    @Test
    void serializesHotspotBottleneckAndExplicitSignals() throws Exception {
        RiskEventResponse response = new RiskEventResponse(1L, 2L, Instant.parse("2026-01-01T00:00:00Z"), 3.2, 80, .5,
                RiskLevel.HIGH, "density is rising", List.of(new HotspotRegion("2,2", 2.1)), true, "demo",
                .45, .35, 25, "SIMULATION");
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(response);
        assertTrue(json.contains("hotspotRegions"));
        assertTrue(json.contains("bottleneckDetected"));
        assertTrue(json.contains("densityChange"));
        assertTrue(json.contains("movementSlowdown"));
        assertTrue(json.contains("hotspotPersistenceSeconds"));
        assertTrue(json.contains("SIMULATION"));
    }
}
