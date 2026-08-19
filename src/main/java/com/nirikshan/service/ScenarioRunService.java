package com.nirikshan.service;

import com.nirikshan.dto.ScenarioRunRequest;
import com.nirikshan.dto.ScenarioRunResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The web backend no longer generates or replays scenario subprocesses.
 * Use replay_scenarios.py locally with --target-url instead.
 */
@Service
public class ScenarioRunService {
    private final CvExecutionPolicy cvPolicy;

    public ScenarioRunService(CvExecutionPolicy cvPolicy) {
        this.cvPolicy = cvPolicy;
    }

    public ScenarioRunResponse start(ScenarioRunRequest request) {
        cvPolicy.requireBackendProcessingEnabled("Scenario replay");
        throw new IllegalStateException("Scenario replay is not available in the web backend");
    }

    public ScenarioRunResponse status(String runId) {
        throw new IllegalArgumentException("Scenario runs are local-only; no backend run exists: " + runId);
    }

    public List<ScenarioRunResponse> active() {
        return List.of();
    }

    public ScenarioRunResponse stop(String runId) {
        throw new IllegalArgumentException("Scenario runs are local-only; no backend run exists: " + runId);
    }
}
