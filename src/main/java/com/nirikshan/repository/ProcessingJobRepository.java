package com.nirikshan.repository;

import com.nirikshan.model.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {
    List<ProcessingJob> findAllByOrderByCreatedAtDesc();
    List<ProcessingJob> findByZone_IdOrderByCreatedAtDesc(Long zoneId);
}
