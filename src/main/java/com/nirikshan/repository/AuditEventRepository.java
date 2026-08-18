package com.nirikshan.repository;

import com.nirikshan.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    long deleteByCreatedAtBefore(Instant cutoff);
}
