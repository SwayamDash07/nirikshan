package com.nirikshan.service;

import com.nirikshan.model.ZoneFeedStatus;
import com.nirikshan.repository.ZoneFeedRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Compatibility coordinator for the legacy feed API.
 *
 * The backend intentionally owns no Python process. Local operators run
 * run_local_cv_workers.py, which owns one process per zone and posts events
 * to this service. Keeping this bean allows existing feed records and API
 * wiring to remain harmless if an older database contains them.
 */
@Service
public class ZoneFeedRunner {
    private static final Logger log = LoggerFactory.getLogger(ZoneFeedRunner.class);
    private final ZoneFeedRepository feedRepository;

    public ZoneFeedRunner(ZoneFeedRepository feedRepository) {
        this.feedRepository = feedRepository;
    }

    @PostConstruct
    void logExternalProcessingMode() {
        log.info("CV execution is external-only; Spring Boot will not launch shared or per-zone Python workers");
    }

    @Scheduled(fixedDelay = 15_000)
    public void reconcile() {
        long active = feedRepository.findByStatus(ZoneFeedStatus.LIVE).size();
        if (active > 0) {
            log.debug("External CV health: active feed records={}, no backend worker is launched", active);
        }
    }

    public void start(Long ignoredZoneId, String ignoredVideoPath) {
        log.info("Zone feed start requested for zone={}, but CV is managed by the local GPU runner", ignoredZoneId);
    }

    public void stop(Long ignoredZoneId) {
        log.info("Zone feed stop requested for zone={}, no backend process to stop", ignoredZoneId);
    }
}
