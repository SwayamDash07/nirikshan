package com.nirikshan.dto;

import com.nirikshan.model.GateActionType;
import com.nirikshan.model.RiskEventSource;

public record GateActionResponse(GateActionType action, String reason, String affectedRoute,
                                 double confidence, RiskEventSource source) { }
