package com.nirikshan.service;

import com.nirikshan.dto.ProcessingJobResponse;
import com.nirikshan.model.*;
import com.nirikshan.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;

@Service
public class ProcessingJobService {
    private final ProcessingJobRepository jobRepository;
    private final ZoneRepository zoneRepository;
    private final Path pipelineDir;
    private final PrivacyAuditService audit;

    public ProcessingJobService(ProcessingJobRepository jobRepository, ZoneRepository zoneRepository,
                                @Value("${nirikshan.cv.pipeline-dir:cv-pipeline}") String pipelineDir, PrivacyAuditService audit) {
        this.jobRepository = jobRepository; this.zoneRepository = zoneRepository;
        this.pipelineDir = Path.of(pipelineDir).toAbsolutePath().normalize();
        this.audit = audit;
    }

    @Transactional
    public ProcessingJobResponse createUpload(Long zoneId, MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("A non-empty video file is required");
        Zone zone = zoneRepository.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("Zone", zoneId));
        String original = file.getOriginalFilename() == null ? "uploaded-video.mp4" : file.getOriginalFilename();
        String safeName = Path.of(original).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        ProcessingJob job = new ProcessingJob();
        job.setZone(zone); job.setVideoFilename(safeName);
        job = jobRepository.save(job);
        Path uploadDir = pipelineDir.resolve("uploads").resolve(job.getId().toString());
        Files.createDirectories(uploadDir);
        try (var input = file.getInputStream()) {
            Files.copy(input, uploadDir.resolve(safeName), StandardCopyOption.REPLACE_EXISTING);
        }
        audit.record("UPLOAD", "PROCESSING_JOB", job.getId(), "Video uploaded for privacy processing");
        return response(job);
    }

    @Transactional(readOnly = true)
    public ProcessingJobResponse get(Long id) { return response(find(id)); }

    @Transactional(readOnly = true)
    public List<ProcessingJobResponse> list(Long zoneId) {
        List<ProcessingJob> jobs = zoneId == null ? jobRepository.findAllByOrderByCreatedAtDesc() : jobRepository.findByZone_IdOrderByCreatedAtDesc(zoneId);
        return jobs.stream().map(this::response).toList();
    }

    @Transactional
    public void markProcessing(Long id) { ProcessingJob job = find(id); job.setStatus(ProcessingJobStatus.PROCESSING); job.setPrivacyStatus(PrivacyStatus.ACTIVE); job.setErrorMessage(null); audit.record("PROCESSING", "PROCESSING_JOB", id, "Privacy processing started"); }

    @Transactional
    public void markComplete(Long id) {
        ProcessingJob job = find(id); job.setStatus(ProcessingJobStatus.COMPLETE); job.setPrivacyStatus(PrivacyStatus.ACTIVE); job.setCompletedAt(Instant.now());
        job.setAnnotatedVideoPath("/job-files/" + id + "/annotated.mp4");
        job.setSummaryPath("/job-files/" + id + "/summary/summary_report.html");
    }

    @Transactional
    public void markFailed(Long id, String error) {
        ProcessingJob job = find(id); job.setStatus(ProcessingJobStatus.FAILED); job.setPrivacyStatus(error != null && error.contains("PRIVACY_PROCESSING_FAILED") ? PrivacyStatus.PRIVACY_PROCESSING_FAILED : PrivacyStatus.PENDING); job.setCompletedAt(Instant.now());
        job.setErrorMessage(error == null ? "CV processing failed" : error.substring(0, Math.min(error.length(), 4000)));
        audit.record("PROCESSING_FAILED", "PROCESSING_JOB", id, job.getPrivacyStatus().name());
    }

    public void deleteUpload(Long id) {
        Path upload = pipelineDir.resolve("uploads").resolve(id.toString());
        try (var stream = Files.walk(upload)) { stream.sorted((a,b) -> b.getNameCount() - a.getNameCount()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); } catch (IOException ignored) { }
        audit.record("DELETION", "UPLOAD", id, "Temporary upload deleted after processing");
    }
    public void deletePrivacyIntermediates(Long id) {
        Path output = pipelineDir.resolve("outputs").resolve(id.toString());
        try (var stream = Files.walk(output)) { stream.filter(path -> path.getFileName().toString().endsWith("_raw.mp4")).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); } catch (IOException ignored) { }
        audit.record("DELETION", "PRIVACY_INTERMEDIATE", id, "Unexposed intermediate annotation removed");
    }

    public Path pipelineDir() { return pipelineDir; }
    @Transactional(readOnly = true) public ProcessingJob find(Long id) { return jobRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Processing job", id)); }
    private ProcessingJobResponse response(ProcessingJob job) { return new ProcessingJobResponse(job.getId(), job.getZone().getId(), job.getZone().getName(), job.getVideoFilename(), job.getStatus(), job.getPrivacyStatus(), job.getCreatedAt(), job.getCompletedAt(), job.getErrorMessage(), job.getAnnotatedVideoPath(), job.getSummaryPath()); }
}
