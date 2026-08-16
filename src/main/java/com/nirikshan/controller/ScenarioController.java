package com.nirikshan.controller;

import com.nirikshan.dto.ScenarioRunRequest;
import com.nirikshan.dto.ScenarioRunResponse;
import com.nirikshan.service.ScenarioRunService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/scenarios")
public class ScenarioController {
    private final ScenarioRunService scenarios;
    public ScenarioController(ScenarioRunService scenarios) { this.scenarios = scenarios; }
    @PostMapping("/run") public ScenarioRunResponse run(@Valid @RequestBody ScenarioRunRequest request) { return scenarios.start(request); }
    @GetMapping("/{runId}/status") public ScenarioRunResponse status(@PathVariable String runId) { return scenarios.status(runId); }
    @PostMapping("/{runId}/stop") public ScenarioRunResponse stop(@PathVariable String runId) { return scenarios.stop(runId); }
    @GetMapping("/active") public List<ScenarioRunResponse> active() { return scenarios.active(); }
}
