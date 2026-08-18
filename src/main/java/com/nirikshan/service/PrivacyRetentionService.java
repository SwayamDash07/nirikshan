package com.nirikshan.service;

import com.nirikshan.repository.AuditEventRepository;
import com.nirikshan.repository.RiskEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class PrivacyRetentionService {
    private final Path pipelineDir;
    private final RiskEventRepository riskEvents;
    private final AuditEventRepository audits;
    private final PrivacyAuditService audit;
    private final long processedHours;
    private final long aggregateDays;
    public PrivacyRetentionService(RiskEventRepository riskEvents, AuditEventRepository audits,
                                   PrivacyAuditService audit,
                                   @Value("${nirikshan.cv.pipeline-dir:cv-pipeline}") String pipelineDir,
                                   @Value("${nirikshan.privacy.processed-frame-retention-hours:24}") long processedHours,
                                   @Value("${nirikshan.privacy.aggregate-event-retention-days:365}") long aggregateDays) {
        this.pipelineDir = Path.of(pipelineDir).toAbsolutePath().normalize(); this.riskEvents = riskEvents; this.audits = audits; this.audit = audit;
        this.processedHours = Math.max(0, processedHours); this.aggregateDays = Math.max(1, aggregateDays);
    }
    @Scheduled(fixedDelayString = "${nirikshan.privacy.cleanup-interval-ms:3600000}")
    public void cleanup() {
        Path root = pipelineDir;
        Path uploads = root.resolve("uploads");
        if (Files.isDirectory(uploads)) {
            try (var stream = Files.list(uploads)) {
                stream.filter(path -> path.getFileName().toString().matches("\\d+")).forEach(path -> deleteMatching(path, ignored -> true));
            } catch (IOException ignored) { }
        }
        if (processedHours == 0) deleteMatching(root.resolve("outputs"), path -> path.getFileName().toString().endsWith("_raw.mp4"));
        deleteExpiredAnnotations(root.resolve("outputs"));
        Instant cutoff = Instant.now().minus(aggregateDays, ChronoUnit.DAYS);
        riskEvents.deleteByTimestampBefore(cutoff);
        audits.deleteByCreatedAtBefore(cutoff);
    }
    public void deleteUpload(Long jobId) { deleteMatching(pipelineDir.resolve("uploads").resolve(jobId.toString()), path -> true); audit.record("DELETION", "UPLOAD", jobId, "Temporary upload deleted"); }
    public void deleteFeedSource(String relativePath, Long zoneId) {
        if (relativePath == null) return;
        Path target = pipelineDir.resolve(relativePath).normalize();
        if (!target.startsWith(pipelineDir)) return;
        try { Files.deleteIfExists(target); audit.record("DELETION", "LIVE_FEED_SOURCE", zoneId, "Active source deleted after coverage stopped"); } catch (IOException ignored) { }
    }
    private void deleteMatching(Path root, java.util.function.Predicate<Path> predicate) {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) { stream.sorted((a,b) -> b.getNameCount()-a.getNameCount()).filter(predicate).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); } catch (IOException ignored) { }
    }
    private void deleteExpiredAnnotations(Path root) {
        if (!Files.exists(root)) return;
        Instant cutoff = Instant.now().minus(processedHours, ChronoUnit.HOURS);
        try (var stream = Files.walk(root)) {
            stream.filter(path -> path.getFileName().toString().equals("annotated.mp4"))
                    .filter(path -> { try { return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff); } catch (IOException ignored) { return false; } })
                    .forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } });
        } catch (IOException ignored) { }
    }
}
