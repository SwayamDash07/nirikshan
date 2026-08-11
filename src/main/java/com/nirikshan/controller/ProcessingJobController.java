package com.nirikshan.controller;

import com.nirikshan.dto.ProcessingJobResponse;
import com.nirikshan.service.JobProcessingRunner;
import com.nirikshan.service.ProcessingJobService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class ProcessingJobController {
    private final ProcessingJobService jobs;
    private final JobProcessingRunner runner;
    public ProcessingJobController(ProcessingJobService jobs, JobProcessingRunner runner) { this.jobs = jobs; this.runner = runner; }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProcessingJobResponse upload(@RequestParam Long zoneId, @RequestParam("file") MultipartFile file) throws IOException {
        ProcessingJobResponse job = jobs.createUpload(zoneId, file);
        runner.processAsync(job.id());
        return job;
    }

    @GetMapping("/{id}") public ProcessingJobResponse get(@PathVariable Long id) { return jobs.get(id); }
    @GetMapping public List<ProcessingJobResponse> list(@RequestParam(required = false) Long zoneId) { return jobs.list(zoneId); }
}
