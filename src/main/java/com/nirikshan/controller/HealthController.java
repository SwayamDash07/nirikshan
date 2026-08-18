package com.nirikshan.controller;

import com.nirikshan.repository.AlertRepository;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.ZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class HealthController {
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private static final long CACHE_MILLIS = 5_000L;
    private static final Set<String> REQUIRED_TABLES = Set.of(
            "venues", "zones", "users", "alerts", "citizen_reports", "processing_jobs", "risk_events", "security_instructions");

    private final ZoneRepository zoneRepository;
    private final RiskEventRepository riskEventRepository;
    private final AlertRepository alertRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private volatile long lastHealthAt;
    private volatile Map<String, Object> cachedHealth;

    public HealthController(ZoneRepository zoneRepository, RiskEventRepository riskEventRepository,
                            AlertRepository alertRepository, JdbcTemplate jdbcTemplate, Environment environment) {
        this.zoneRepository = zoneRepository;
        this.riskEventRepository = riskEventRepository;
        this.alertRepository = alertRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @GetMapping("/api/health")
    public synchronized ResponseEntity<Map<String, Object>> health() {
        if (cachedHealth != null && System.currentTimeMillis() - lastHealthAt < CACHE_MILLIS) return ResponseEntity.ok(cachedHealth);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("activeProfile", activeProfile());
        try {
            Map<String, Object> database = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Map<String, Object>>) connection -> {
                DatabaseMetaData metadata = connection.getMetaData();
                List<String> tables = new ArrayList<>();
                try (var result = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (result.next()) tables.add(result.getString("TABLE_NAME").toLowerCase());
                }
                List<String> missing = REQUIRED_TABLES.stream().filter(table -> !tables.contains(table)).sorted().toList();
                return Map.of(
                        "database", connection.getCatalog(),
                        "schema", connection.getSchema(),
                        "schemaReady", missing.isEmpty(),
                        "missingTables", missing);
            });
            body.put("database", database);
            body.put("totalZones", zoneRepository.count());
            body.put("totalRiskEvents", riskEventRepository.count());
            body.put("activeAlerts", alertRepository.countByResolvedFalse());
            if (Boolean.FALSE.equals(((Map<?, ?>) database).get("schemaReady"))) {
                body.put("status", "DOWN");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
            }
            cachedHealth = Map.copyOf(body);
            lastHealthAt = System.currentTimeMillis();
            return ResponseEntity.ok(cachedHealth);
        } catch (DataAccessException exception) {
            log.error("Health check could not access the configured database", exception);
            body.put("status", "DOWN");
            body.put("databaseError", "Database connection or schema check failed");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? environment.getDefaultProfiles()[0] : String.join(",", profiles);
    }
}
