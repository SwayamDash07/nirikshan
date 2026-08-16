package com.nirikshan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ScenarioRunRequest(@NotNull String scenarioType, @NotNull Long zoneId, @Positive Double speed) { }
