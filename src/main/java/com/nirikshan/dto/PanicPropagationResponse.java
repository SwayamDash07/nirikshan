package com.nirikshan.dto;

import com.nirikshan.model.RiskEventSource;
import java.util.List;

public record PanicPropagationResponse(String state, Long sourceZoneId, String sourceZoneName,
                                       List<Long> affectedZoneIds, double confidence, String explanation,
                                       RiskEventSource source) {
    public static PanicPropagationResponse insufficient(RiskEventSource source) {
        return new PanicPropagationResponse("INSUFFICIENT_DATA", null, null, List.of(), 0,
                "Cross-zone propagation cannot be assessed without recent readings in connected zones.", source);
    }
}
