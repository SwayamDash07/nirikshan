package com.nirikshan.repository;

import com.nirikshan.model.CitizenReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CitizenReportRepository extends JpaRepository<CitizenReport, Long> {
    List<CitizenReport> findByZone_IdOrderByTimestampDesc(Long zoneId);
    List<CitizenReport> findAllByOrderByTimestampDesc();
}
