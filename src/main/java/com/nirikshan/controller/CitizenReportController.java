package com.nirikshan.controller;

import com.nirikshan.dto.CitizenReportRequest;
import com.nirikshan.dto.CitizenReportResponse;
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
        if (request.clientEventId() != null && !request.clientEventId().isBlank()) {
            var existing = reportRepository.findByClientEventId(request.clientEventId());
            if (existing.isPresent()) return existing.get();
        }
        Zone zone = zoneRepository.findById(request.zoneId()).orElseThrow(() -> new ResourceNotFoundException("Zone", request.zoneId()));
        CitizenReport report = new CitizenReport(zone, request.description());
        report.setClientEventId(request.clientEventId());
        return reportRepository.save(report);
    }

    @GetMapping
    public List<CitizenReportResponse> list(@RequestParam(required = false) Long zoneId) {
        List<CitizenReport> reports = zoneId == null ? reportRepository.findAllByOrderByTimestampDesc() : reportRepository.findByZone_IdOrderByTimestampDesc(zoneId);
        return reports.stream().map(CitizenReportController::response).toList();
    }

    public static CitizenReportResponse response(CitizenReport report) {
        return new CitizenReportResponse(report.getId(), report.getZoneId(), report.getZone().getName(), report.getDescription(), report.getTimestamp(), report.getStatus().name());
    }
}
