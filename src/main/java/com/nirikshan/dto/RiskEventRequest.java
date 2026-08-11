package com.nirikshan.dto;

import com.nirikshan.model.RiskLevel;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record RiskEventRequest(
        @NotNull Long zoneId,
        @NotNull Instant timestamp,
        @PositiveOrZero double densityScore,
        @PositiveOrZero int peopleCount,
        @PositiveOrZero double movementSpeed,
        @NotNull RiskLevel riskLevel,
        @NotBlank String explanation,
        String sourceClipId) {}
