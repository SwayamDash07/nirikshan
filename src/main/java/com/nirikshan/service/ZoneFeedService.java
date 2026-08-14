package com.nirikshan.service;

import com.nirikshan.dto.AdminZoneResponse;
import com.nirikshan.model.Zone;
import com.nirikshan.model.ZoneFeed;
import com.nirikshan.model.ZoneFeedStatus;
import com.nirikshan.repository.ZoneFeedRepository;
import com.nirikshan.repository.ZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;

@Service
public class ZoneFeedService {
    private final ZoneRepository zoneRepository;
    private final ZoneFeedRepository feedRepository;
    private final ZoneFeedRunner runner;
    private final Path pipelineDir;

    public ZoneFeedService(ZoneRepository zoneRepository, ZoneFeedRepository feedRepository, ZoneFeedRunner runner,
                           @Value("${nirikshan.cv.pipeline-dir:cv-pipeline}") String pipelineDir) {
        this.zoneRepository = zoneRepository;
        this.feedRepository = feedRepository;
        this.runner = runner;
        this.pipelineDir = Path.of(pipelineDir).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<AdminZoneResponse> list() {
        return zoneRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream().map(this::response).toList();
    }

    @Transactional
    public AdminZoneResponse connect(Long zoneId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("A non-empty video file is required to connect footage");
        Zone zone = zoneRepository.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("Zone", zoneId));
        runner.stop(zoneId);

        String original = file.getOriginalFilename() == null ? "camera-footage.mp4" : file.getOriginalFilename();
        String safeName = Path.of(original).getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedName = Instant.now().toEpochMilli() + "-" + safeName;
        Path relativePath = Path.of("uploads", "feeds", "zone-" + zoneId, storedName);
        Path destination = pipelineDir.resolve(relativePath);
        Files.createDirectories(destination.getParent());
        try (var input = file.getInputStream()) {
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        ZoneFeed feed = feedRepository.findByZone_Id(zoneId).orElseGet(ZoneFeed::new);
        feed.setZone(zone);
        feed.setVideoPath(relativePath.toString());
        feed.setVideoFilename(safeName);
        feed.setStatus(ZoneFeedStatus.LIVE);
        feed.setStartedAt(Instant.now());
        feed.setCurrentLoopIteration(0);
        feed = feedRepository.save(feed);
        runner.start(zoneId, feed.getVideoPath());
        return response(zone, feed);
    }

    @Transactional
    public AdminZoneResponse stop(Long zoneId) {
        ZoneFeed feed = feedRepository.findByZone_Id(zoneId).orElseThrow(() -> new ResourceNotFoundException("Zone feed", zoneId));
        runner.stop(zoneId);
        feed.setStatus(ZoneFeedStatus.OFFLINE);
        feed.setStartedAt(null);
        feedRepository.save(feed);
        return response(feed.getZone(), feed);
    }

    private AdminZoneResponse response(Zone zone) {
        return feedRepository.findByZone_Id(zone.getId()).map(feed -> response(zone, feed)).orElseGet(() -> response(zone, null));
    }

    private AdminZoneResponse response(Zone zone, ZoneFeed feed) {
        ZoneFeedStatus status = feed == null ? ZoneFeedStatus.OFFLINE : feed.getStatus();
        String videoUrl = feed == null ? null : "/feed-files/" + feed.getVideoPath().replace('\\', '/');
        return new AdminZoneResponse(zone.getId(), zone.getName(), zone.getLatitude(), zone.getLongitude(), zone.getRadiusMeters(),
                zone.getCurrentDensity(), zone.getCurrentPeopleCount(), zone.getCurrentRiskLevel(), zone.getLastUpdated(), status,
                feed == null ? null : feed.getVideoFilename(), videoUrl, feed == null ? null : feed.getStartedAt(),
                feed == null ? 0 : feed.getCurrentLoopIteration());
    }
}
