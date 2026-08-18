package com.nirikshan.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirikshan.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JobProcessingRunner {
    private static final Logger log = LoggerFactory.getLogger(JobProcessingRunner.class);
    private final ProcessingJobService jobs;
    private final RiskEventService riskEvents;
    private final ObjectMapper objectMapper;
    private final String pythonExecutable;
    private final PrivacyAuditService audit;

    public JobProcessingRunner(ProcessingJobService jobs, RiskEventService riskEvents, ObjectMapper objectMapper,
                               @Value("${nirikshan.cv.python-executable:python}") String pythonExecutable, PrivacyAuditService audit) {
        this.jobs = jobs; this.riskEvents = riskEvents; this.objectMapper = objectMapper; this.pythonExecutable = pythonExecutable; this.audit = audit;
    }

    @PostConstruct
    void logPythonConfiguration() {
        String configured = System.getenv("NIRIKSHAN_PYTHON");
        if (configured == null || configured.isBlank()) {
            log.warn("NIRIKSHAN_PYTHON is not set; CV jobs will fall back to system Python: {}", pythonExecutable);
        } else {
            log.info("NIRIKSHAN_PYTHON configured for CV jobs: {}", pythonExecutable);
        }
    }

    @Async
    public void processAsync(Long jobId) {
        try {
            log.info("Starting CV processing job {} with Python executable: {}", jobId, pythonExecutable);
            jobs.markProcessing(jobId);
            ProcessingJobResponse job = jobs.get(jobId);
            Path pipelineDir = jobs.pipelineDir();
            String relativeInput = "uploads/" + jobId + "/" + job.videoFilename();
            String relativeOutput = "outputs/" + jobId;
            Path eventsFile = pipelineDir.resolve(relativeOutput).resolve("events.json");
            run(pipelineDir, List.of(pythonExecutable, "process_video.py", "--input", relativeInput,
                    "--zone-id", job.zoneId().toString(), "--thresholds", "thresholds_config.json",
                    "--output", relativeOutput + "/events.json", "--annotate",
                    "--annotation-output", relativeOutput + "/annotated.mp4"));
            run(pipelineDir, List.of(pythonExecutable, "generate_summary.py", "--events", relativeOutput + "/events.json",
                    "--output-dir", relativeOutput + "/summary"));

            List<RiskEventRequest> events = objectMapper.readValue(eventsFile.toFile(), new TypeReference<>() {});
            for (RiskEventRequest event : events) riskEvents.ingest(event);
            jobs.markComplete(jobId);
            audit.record("PROCESSING_COMPLETE", "PROCESSING_JOB", jobId, "Aggregate events ingested; sanitized annotation retained per policy");
        } catch (Exception error) {
            log.error("CV processing job {} failed", jobId, error);
            jobs.markFailed(jobId, error.getMessage());
        } finally {
            jobs.deleteUpload(jobId);
            jobs.deletePrivacyIntermediates(jobId);
        }
    }

    private void run(Path pipelineDir, List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(pipelineDir.toFile()).redirectErrorStream(true).start();
        String output;
        try (var stream = process.getInputStream()) { output = new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new IOException("Command failed (exit " + exitCode + "): " + tail(output));
    }

    private String tail(String output) {
        int start = Math.max(0, output.length() - 3500);
        return output.substring(start).trim();
    }
}
