package com.nirikshan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Compatibility hook for old job records; it never starts a subprocess. */
@Service
public class JobProcessingRunner {
    private static final Logger log = LoggerFactory.getLogger(JobProcessingRunner.class);
    private final ProcessingJobService jobs;
    private final CvExecutionPolicy cvPolicy;

    public JobProcessingRunner(ProcessingJobService jobs, CvExecutionPolicy cvPolicy) {
        this.jobs = jobs;
        this.cvPolicy = cvPolicy;
    }

    @Async
    public void processAsync(Long jobId) {
        if (!cvPolicy.backendProcessingEnabled()) {
            log.warn("Rejected legacy CV job {}: backend CV processing is disabled; run process_video.py locally", jobId);
            jobs.markFailed(jobId, "Backend CV processing is disabled. Run the local GPU worker and POST risk events instead.");
        }
    }
}
