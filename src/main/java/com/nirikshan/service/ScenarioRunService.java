package com.nirikshan.service;

import com.nirikshan.dto.ScenarioRunRequest;
import com.nirikshan.dto.ScenarioRunResponse;
import com.nirikshan.model.Zone;
import com.nirikshan.repository.ZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScenarioRunService {
    private final ZoneRepository zones;
    private final RiskEventService riskEvents;
    private final Path pipelineDir;
    private final String python;
    private final String riskEventUrl;
    private final Map<String, Run> runs = new ConcurrentHashMap<>();

    public ScenarioRunService(ZoneRepository zones, RiskEventService riskEvents,
                              @Value("${nirikshan.cv.pipeline-dir:cv-pipeline}") String pipelineDir,
                              @Value("${nirikshan.cv.python-executable:python}") String python,
                              @Value("${nirikshan.cv.risk-event-url:http://localhost:8080/api/risk-events}") String riskEventUrl) {
        this.zones = zones; this.riskEvents = riskEvents; this.pipelineDir = Path.of(pipelineDir).toAbsolutePath().normalize(); this.python = python; this.riskEventUrl = riskEventUrl;
    }

    public ScenarioRunResponse start(ScenarioRunRequest request) {
        Zone zone = zones.findById(request.zoneId()).orElseThrow(() -> new ResourceNotFoundException("Zone", request.zoneId()));
        String scenario = normalize(request.scenarioType());
        double speed = request.speed() == null ? 20 : request.speed();
        String id = UUID.randomUUID().toString();
        Run run = new Run(id, scenario, zone.getId(), speed);
        runs.put(id, run);
        CompletableFuture.runAsync(() -> execute(run));
        return response(run);
    }

    public ScenarioRunResponse status(String runId) { return response(find(runId)); }

    public List<ScenarioRunResponse> active() { return runs.values().stream().filter(run -> run.status.equals("PENDING") || run.status.equals("RUNNING")).map(this::response).toList(); }

    public ScenarioRunResponse stop(String runId) {
        Run run = find(runId);
        if (run.status.equals("PENDING") || run.status.equals("RUNNING")) {
            run.stopRequested = true;
            Process process = run.process;
            if (process != null) kill(process);
            run.status = "STOPPED"; run.completedAt = Instant.now(); run.message = "Stopped by administrator.";
            riskEvents.restoreZoneFromLive(run.zoneId);
        }
        return response(run);
    }

    private void execute(Run run) {
        Path output = pipelineDir.resolve("outputs").resolve("scenarios").resolve(run.id).resolve("events.json");
        try {
            Files.createDirectories(output.getParent());
            if (run.stopRequested) return;
            run.status = "RUNNING";
            run(run, List.of(python, "replay_scenarios.py", generatorScenario(run.scenario), "--zone-id", run.zoneId.toString(), "--output", pipelineDir.relativize(output).toString()));
            if (!run.stopRequested) run(run, List.of(python, "replay_events.py", "--events", pipelineDir.relativize(output).toString(), "--url", riskEventUrl, "--speed", Double.toString(run.speed), "--rebase-now"));
            if (!run.stopRequested) { run.status = "COMPLETE"; run.message = "Scenario replay completed."; }
        } catch (Exception error) {
            if (!run.stopRequested) { run.status = "COMPLETE"; run.message = "Scenario replay failed: " + compact(error.getMessage()); }
        } finally {
            run.completedAt = Instant.now(); run.process = null;
            riskEvents.restoreZoneFromLive(run.zoneId);
        }
    }

    private void run(Run run, List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(pipelineDir.toFile()).redirectErrorStream(true).start();
        run.process = process;
        String output;
        try (var stream = process.getInputStream()) { output = new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
        int exit = process.waitFor();
        if (exit != 0 && !run.stopRequested) throw new IOException("Command failed (exit " + exit + "): " + compact(output));
    }

    private void kill(Process process) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
    private Run find(String id) { Run run = runs.get(id); if (run == null) throw new IllegalArgumentException("Scenario run not found: " + id); return run; }
    private String normalize(String value) { return switch (value.toLowerCase().trim()) { case "buildup", "surge", "persistent_hotspot", "slowdown", "recovery" -> value.toLowerCase().trim(); default -> throw new IllegalArgumentException("scenarioType must be buildup, surge, persistent_hotspot, slowdown, or recovery"); }; }
    private String generatorScenario(String value) { return value.equals("persistent_hotspot") ? "persistent" : value; }
    private ScenarioRunResponse response(Run run) { return new ScenarioRunResponse(run.id, run.scenario, run.zoneId, run.status, run.speed, run.startedAt, run.completedAt, run.message); }
    private String compact(String message) { if (message == null) return "unknown error"; return message.replaceAll("\\s+", " ").trim().substring(0, Math.min(500, message.replaceAll("\\s+", " ").trim().length())); }

    private static final class Run {
        final String id, scenario; final Long zoneId; final double speed; final Instant startedAt = Instant.now();
        volatile String status = "PENDING", message = "Queued for replay."; volatile Instant completedAt; volatile boolean stopRequested; volatile Process process;
        Run(String id, String scenario, Long zoneId, double speed) { this.id = id; this.scenario = scenario; this.zoneId = zoneId; this.speed = speed; }
    }
}
