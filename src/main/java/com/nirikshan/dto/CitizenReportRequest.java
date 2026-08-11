package com.nirikshan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CitizenReportRequest(@NotNull Long zoneId, @NotBlank String description) {}
