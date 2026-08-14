package com.nirikshan.service;

import com.nirikshan.model.ZoneFeedStatus;
import com.nirikshan.repository.ZoneFeedRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
        Process process;
        try {
            process = launchIfCurrent(zoneId, videoPath, output);
            if (process == null) return;
            log.info("Started continuous camera loop for zone {} with {}", zoneId, videoPath);
            try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = outputReader.readLine()) != null) {
                    if (line.startsWith("LOOP_ITERATION ")) {
                        try { updateIteration(zoneId, videoPath, Integer.parseInt(line.substring("LOOP_ITERATION ".length()).trim())); }
                        catch (NumberFormatException ignored) { log.debug("Could not parse loop iteration: {}", line); }
                    } else if (!line.isBlank()) {
                        log.debug("CV zone {}: {}", zoneId, line);
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) log.warn("Continuous camera loop for zone {} exited with code {}", zoneId, exitCode);
        } catch (Exception error) {
            log.error("Continuous camera loop for zone {} failed", zoneId, error);
        } finally {
            Process current = processes.get(zoneId);
            if (current != null && !current.isAlive()) processes.remove(zoneId, current);
            markOfflineIfCurrent(zoneId, videoPath);
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
                "--loop", "--post-live", "--post-url", riskEventUrl
        );
        Process process = new ProcessBuilder(command)
                .directory(pipelineDir.toFile())
                .redirectErrorStream(true)
                .start();
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
}
