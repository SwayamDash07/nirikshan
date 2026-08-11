package com.nirikshan.controller;

import com.nirikshan.dto.CitizenReportRequest;
import com.nirikshan.model.CitizenReport;
import com.nirikshan.model.Zone;
import com.nirikshan.repository.CitizenReportRepository;
import com.nirikshan.repository.ZoneRepository;
import com.nirikshan.service.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/citizen-reports")
public class CitizenReportController {
    private final CitizenReportRepository reportRepository;
    private final ZoneRepository zoneRepository;

    public CitizenReportController(CitizenReportRepository reportRepository, ZoneRepository zoneRepository) {
        this.reportRepository = reportRepository; this.zoneRepository = zoneRepository;
    }

    @PostMapping
    public CitizenReport create(@Valid @RequestBody CitizenReportRequest request) {
        Zone zone = zoneRepository.findById(request.zoneId()).orElseThrow(() -> new ResourceNotFoundException("Zone", request.zoneId()));
        return reportRepository.save(new CitizenReport(zone, request.description()));
    }

    @GetMapping
    public List<CitizenReport> list(@RequestParam(required = false) Long zoneId) {
        return zoneId == null ? reportRepository.findAllByOrderByTimestampDesc() : reportRepository.findByZone_IdOrderByTimestampDesc(zoneId);
    }
}
