package com.nirikshan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nirikshan.model.PrivacyStatus;
import com.nirikshan.model.ZoneFeed;
import com.nirikshan.model.ZoneFeedStatus;
import com.nirikshan.repository.ZoneFeedRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Owns one shared Python CV process for every active video zone. */
@Service
public class ZoneFeedRunner {
    private static final Logger log = LoggerFactory.getLogger(ZoneFeedRunner.class);
    private final ZoneFeedRepository feedRepository;
    private final ObjectMapper objectMapper;
    private final Path pipelineDir;
    private final String pythonExecutable;
    private final String riskEventUrl;
    private final Path manifest;
    private Process process;

    public ZoneFeedRunner(ZoneFeedRepository feedRepository, ObjectMapper objectMapper,
                          @Value("${nirikshan.cv.pipeline-dir:cv-pipeline}") String pipelineDir,
                          @Value("${nirikshan.cv.python-executable:python}") String pythonExecutable,
                          @Value("${nirikshan.cv.risk-event-url:http://localhost:8080/api/risk-events}") String riskEventUrl) {
        this.feedRepository = feedRepository;
        this.objectMapper = objectMapper;
        this.pipelineDir = Path.of(pipelineDir).toAbsolutePath().normalize();
        this.pythonExecutable = pythonExecutable;
        this.riskEventUrl = riskEventUrl;
        this.manifest = this.pipelineDir.resolve("outputs/live/shared-zones.json");
    }

    @PostConstruct
    void restartPersistedLiveFeeds() { reconcile(); }

    @PreDestroy
    void stopAll() { synchronized (this) { stopProcess(); } }

    /** Reconciles active database feeds with the one shared worker. */
    @Scheduled(fixedDelay = 15_000)
    public synchronized void reconcile() {
        try {
            List<Map<String, Object>> active = feedRepository.findByStatus(ZoneFeedStatus.LIVE).stream()
                    .filter(feed -> Files.exists(pipelineDir.resolve(feed.getVideoPath())))
                    .map(this::manifestEntry)
                    .toList();
            Files.createDirectories(manifest.getParent());
            objectMapper.writeValue(manifest.toFile(), active);
            if (active.isEmpty()) {
                stopProcess();
            } else if (process == null || !process.isAlive()) {
                launchSharedWorker();
            }
            log.info("Shared CV health: activeZones={}, pid={}, alive={}", active.size(),
                    process == null ? "none" : process.pid(), process != null && process.isAlive());
        } catch (Exception error) {
            log.error("Shared CV worker reconciliation failed", error);
        }
    }

    /** Called after a feed transaction commits. */
    public synchronized void start(Long ignoredZoneId, String ignoredVideoPath) { reconcile(); }

    /** Refreshes the manifest without stopping other zones. */
    public synchronized void stop(Long ignoredZoneId) { reconcile(); }

    private Map<String, Object> manifestEntry(ZoneFeed feed) {
        return Map.of(
                "zoneId", feed.getZone().getId(),
                "input", feed.getVideoPath(),
                "sourceClipId", feed.getVideoFilename()
        );
    }

    private void launchSharedWorker() throws IOException {
        List<String> command = List.of(
                pythonExecutable, "shared_worker.py",
                "--manifest", pipelineDir.relativize(manifest).toString(),
                "--thresholds", "thresholds_config.json",
                "--model", "yolov8n.pt",
                "--post-url", riskEventUrl
        );
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(pipelineDir.toFile())
                .redirectErrorStream(true);
        builder.environment().putIfAbsent("OMP_NUM_THREADS", "1");
        builder.environment().putIfAbsent("MKL_NUM_THREADS", "1");
        builder.environment().putIfAbsent("OPENBLAS_NUM_THREADS", "1");
        builder.environment().putIfAbsent("NUMEXPR_NUM_THREADS", "1");
        process = builder.start();
        log.info("Started shared CV worker pid={} for active video zones", process.pid());
        java.util.concurrent.CompletableFuture.runAsync(() -> monitorProcess(process));
    }

    private void monitorProcess(Process observed) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(observed.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) log.info("Shared CV: {}", line);
            }
            int exitCode = observed.waitFor();
            log.warn("Shared CV worker exited with exitCode={}, pid={}", exitCode, observed.pid());
            if (exitCode == 137) log.error("Shared CV worker was killed by the container memory limit");
        } catch (Exception error) {
            log.error("Shared CV worker monitor failed", error);
        } finally {
            synchronized (this) {
                if (process == observed) process = null;
            }
        }
    }

    private void stopProcess() {
        if (process == null) return;
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        log.info("Stopped shared CV worker");
        process = null;
    }
}
