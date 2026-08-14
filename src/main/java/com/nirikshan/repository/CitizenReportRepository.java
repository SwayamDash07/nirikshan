package com.nirikshan.repository;

import com.nirikshan.model.CitizenReport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CitizenReportRepository extends JpaRepository<CitizenReport, Long> {
    @EntityGraph(attributePaths = "zone")
    List<CitizenReport> findByZone_IdOrderByTimestampDesc(Long zoneId);
    @EntityGraph(attributePaths = "zone")
    List<CitizenReport> findAllByOrderByTimestampDesc();
}
