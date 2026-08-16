package com.nirikshan.dto;

import java.util.List;

/** Bounded, deterministic decision support; this is not a medical or guaranteed prediction. */
public record StampedeLikelihoodResponse(double score, String level, List<String> evidence, String explanation) {
    public static StampedeLikelihoodResponse insufficient(String explanation) {
        return new StampedeLikelihoodResponse(0, "INSUFFICIENT_DATA", List.of(), explanation);
    }
}
