package com.nirikshan.dto;

import java.util.List;

public record UnusualBehaviorResponse(boolean detected, String state, int persistentReadings,
                                      double confidence, List<String> evidence, String explanation) {
    public static UnusualBehaviorResponse insufficient(String explanation) {
        return new UnusualBehaviorResponse(false, "INSUFFICIENT_DATA", 0, 0, List.of(), explanation);
    }
}
