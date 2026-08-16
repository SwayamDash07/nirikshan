package com.nirikshan.repository;
import com.nirikshan.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.Instant;
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByResolvedOrderByTimestampDesc(boolean resolved);
    List<Alert> findAllByOrderByTimestampDesc();
    long countByResolvedFalse();
    List<Alert> findByZoneIdAndTimestampAfterOrderByTimestampAsc(Long zoneId, Instant timestamp);
    List<Alert> findByZoneIdAndResolvedFalse(Long zoneId);
    List<Alert> findByZoneIdAndResolvedFalseOrderByTimestampDesc(Long zoneId);
}
