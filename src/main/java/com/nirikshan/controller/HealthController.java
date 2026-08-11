package com.nirikshan.controller;

import com.nirikshan.repository.AlertRepository;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.ZoneRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final ZoneRepository zoneRepository;
    private final RiskEventRepository riskEventRepository;
    private final AlertRepository alertRepository;

    public HealthController(ZoneRepository zoneRepository, RiskEventRepository riskEventRepository, AlertRepository alertRepository) {
        this.zoneRepository = zoneRepository;
        this.riskEventRepository = riskEventRepository;
        this.alertRepository = alertRepository;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "totalZones", zoneRepository.count(),
                "totalRiskEvents", riskEventRepository.count(),
                "activeAlerts", alertRepository.countByResolvedFalse()
        );
    }
}
