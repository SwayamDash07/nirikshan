package com.nirikshan.service;

import com.nirikshan.model.ZoneFeedStatus;
import com.nirikshan.model.PrivacyStatus;
import com.nirikshan.repository.ZoneFeedRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ZoneFeedRunner {
    private static final Logger log = LoggerFactory.getLogger(ZoneFeedRunner.class);
    private static final int HEALTH_LOG_EVERY_ITERATIONS = 5;
    private static final long RETRY_DELAY_MILLIS = 3_000L;
    private final ZoneFeedRepository feedRepository;
    private final Path pipelineDir;
    private final String pythonExecutable;
    private final String riskEventUrl;
    private final Map<Long, Process> processes = new ConcurrentHashMap<>();

    public ZoneFeedRunner(ZoneFeedRepository feedRepository,
                          @Value("${nirikshan.cv.pipeline-dir:cv-pipeline}") String pipelineDir,
                          @Value("${nirikshan.cv.python-executable:python}") String pythonExecutable,
                          @Value("${nirikshan.cv.risk-event-url:http://localhost:8080/api/risk-events}") String riskEventUrl) {
        this.feedRepository = feedRepository;
        this.pipelineDir = Path.of(pipelineDir).toAbsolutePath().normalize();
        this.pythonExecutable = pythonExecutable;
        this.riskEventUrl = riskEventUrl;
    }

    @PostConstruct
    void restartPersistedLiveFeeds() {
        List<com.nirikshan.model.ZoneFeed> persisted = feedRepository.findByStatus(ZoneFeedStatus.LIVE);
        log.info("Reconciling {} persisted LIVE zone feeds after backend startup", persisted.size());
        persisted.forEach(feed -> {
            if (!java.nio.file.Files.exists(pipelineDir.resolve(feed.getVideoPath()))) {
                log.warn("Zone {} was LIVE but its video file is missing; marking feed OFFLINE", feed.getZone().getId());
                feed.setStatus(ZoneFeedStatus.OFFLINE);
                feed.setStartedAt(null);
                feedRepository.save(feed);
            } else {
                if (feed.getPrivacyStatus() != PrivacyStatus.ACTIVE) feed.setPrivacyStatus(PrivacyStatus.ACTIVE);
                log.info("Resuming persisted LIVE zone {} from {}", feed.getZone().getId(), feed.getVideoPath());
                java.util.concurrent.CompletableFuture.runAsync(() -> start(feed.getZone().getId(), feed.getVideoPath()));
            }
        });
    }

    @jakarta.annotation.PreDestroy
    void stopAll() {
        processes.keySet().forEach(this::stop);
    }

    @Scheduled(fixedDelay = 60_000)
    void logHealth() {
        feedRepository.findByStatus(ZoneFeedStatus.LIVE).forEach(feed -> {
            Process process = processes.get(feed.getZone().getId());
            log.info("ZoneFeed health: zone={}, dbStatus={}, pid={}, alive={}, loopIteration={}",
                    feed.getZone().getId(), feed.getStatus(), process == null ? "none" : process.pid(),
                    process != null && process.isAlive(), feed.getCurrentLoopIteration());
            if (process == null || !process.isAlive()) markOfflineIfCurrent(feed.getZone().getId(), feed.getVideoPath());
        });
    }

    public synchronized void stop(Long zoneId) {
        Process process = processes.remove(zoneId);
        if (process == null) return;
        process.destroy();
        try {
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        log.info("Stopped continuous camera loop for zone {}", zoneId);
    }

    @Async
    public void start(Long zoneId, String videoPath) {
        Path output = pipelineDir.resolve("outputs").resolve("live").resolve("zone-" + zoneId).resolve("events.json");
        try {
            while (isCurrentFeed(zoneId, videoPath)) {
            Process activeProcess = launchIfCurrent(zoneId, videoPath, output);
                if (activeProcess == null) return;
                log.info("Started continuous camera loop for zone {} with {} (pid={})", zoneId, videoPath, activeProcess.pid());
                boolean privacyFailed = false;
                try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = outputReader.readLine()) != null) {
                        if (line.contains("PRIVACY_PROCESSING_FAILED")) {
                            privacyFailed = true;
                            markPrivacyFailed(zoneId, videoPath);
                            log.error("Privacy processing failed for zone {}; refusing to expose or retry footage", zoneId);
                            break;
                        }
                        if (line.startsWith("LOOP_ITERATION ")) {
                            try {
                                int iteration = Integer.parseInt(line.substring("LOOP_ITERATION ".length()).trim());
                                if (iteration % HEALTH_LOG_EVERY_ITERATIONS == 0) updateIteration(zoneId, videoPath, iteration);
                                if (iteration > 0 && iteration % HEALTH_LOG_EVERY_ITERATIONS == 0) {
                                    log.info("Zone {} loop still running, iteration count: {}, pid={}", zoneId, iteration, activeProcess.pid());
                                }
                            }
                            catch (NumberFormatException ignored) { log.debug("Could not parse loop iteration: {}", line); }
                        } else if (!line.isBlank()) {
                            if (line.startsWith("Error:") || line.contains("Traceback") || line.contains("Exception")) {
                                log.warn("CV zone {}: {}", zoneId, line);
                            } else {
                                log.debug("CV zone {}: {}", zoneId, line);
                            }
                        }
                    }
                }
                int exitCode = activeProcess.waitFor();
                log.warn("CV worker exited for zone {} with exitCode={}, pid={}, currentFeed={}",
                        zoneId, exitCode, activeProcess.pid(), isCurrentFeed(zoneId, videoPath));
                if (exitCode == 137) {
                    log.error("CV worker for zone {} was killed for exceeding the container memory limit; "
                            + "not restarting automatically", zoneId);
                    markOfflineIfCurrent(zoneId, videoPath);
                    break;
                }
                if (privacyFailed || !isCurrentFeed(zoneId, videoPath)) break;
                log.warn("Continuous camera loop for zone {} stopped with exit code {} (pid={}); retrying", zoneId, exitCode, activeProcess.pid());
                Thread.sleep(RETRY_DELAY_MILLIS);
            }
        } catch (Exception error) {
            log.error("Continuous camera loop for zone {} failed", zoneId, error);
        } finally {
            Process current = processes.get(zoneId);
            if (current != null && !current.isAlive()) processes.remove(zoneId, current);
            markOfflineIfCurrent(zoneId, videoPath);
            log.info("Zone {} worker reconciliation complete; active process={}", zoneId,
                    processes.get(zoneId) == null ? "none" : processes.get(zoneId).pid());
        }
    }

    private synchronized Process launchIfCurrent(Long zoneId, String videoPath, Path output) throws IOException {
        if (!isCurrentFeed(zoneId, videoPath)) return null;
        stop(zoneId);
        if (!isCurrentFeed(zoneId, videoPath)) return null;
        java.nio.file.Files.createDirectories(output.getParent());
        List<String> command = List.of(
                pythonExecutable, "process_video.py",
                "--input", videoPath,
                "--zone-id", zoneId.toString(),
                "--thresholds", "thresholds_config.json",
                "--output", pipelineDir.relativize(output).toString(),
                "--model", "yolov8n.pt",
                "--loop", "--post-live", "--post-url", riskEventUrl
        );
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(pipelineDir.toFile())
                .redirectErrorStream(true);
        // Keep one lightweight CPU worker from creating a large native thread pool
        // inside the small Railway container. This is especially important when
        // more than one campus zone is connected at the same time.
        builder.environment().putIfAbsent("OMP_NUM_THREADS", "1");
        builder.environment().putIfAbsent("MKL_NUM_THREADS", "1");
        builder.environment().putIfAbsent("OPENBLAS_NUM_THREADS", "1");
        builder.environment().putIfAbsent("NUMEXPR_NUM_THREADS", "1");
        Process process = builder.start();
        processes.put(zoneId, process);
        return process;
    }

    private boolean isCurrentFeed(Long zoneId, String videoPath) {
        return feedRepository.findByZone_Id(zoneId)
                .map(feed -> feed.getStatus() == ZoneFeedStatus.LIVE && videoPath.equals(feed.getVideoPath()))
                .orElse(false);
    }

    private void updateIteration(Long zoneId, String videoPath, int iteration) {
        feedRepository.findByZone_Id(zoneId).filter(feed -> feed.getStatus() == ZoneFeedStatus.LIVE && videoPath.equals(feed.getVideoPath())).ifPresent(feed -> {
            feed.setCurrentLoopIteration(iteration);
            feedRepository.save(feed);
        });
    }

    private void markOfflineIfCurrent(Long zoneId, String videoPath) {
        feedRepository.findByZone_Id(zoneId).filter(feed -> feed.getStatus() == ZoneFeedStatus.LIVE && videoPath.equals(feed.getVideoPath())).ifPresent(feed -> {
            feed.setStatus(ZoneFeedStatus.OFFLINE);
            feedRepository.save(feed);
        });
    }

    private void markPrivacyFailed(Long zoneId, String videoPath) {
        feedRepository.findByZone_Id(zoneId).filter(feed -> videoPath.equals(feed.getVideoPath())).ifPresent(feed -> {
            feed.setStatus(ZoneFeedStatus.OFFLINE);
            feed.setPrivacyStatus(PrivacyStatus.PRIVACY_PROCESSING_FAILED);
            feedRepository.save(feed);
        });
    }
}
