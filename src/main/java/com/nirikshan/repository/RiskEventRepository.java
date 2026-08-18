package com.nirikshan.repository;
import com.nirikshan.model.RiskEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.Instant;
public interface RiskEventRepository extends JpaRepository<RiskEvent, Long> {
    List<RiskEvent> findByZoneIdOrderByTimestampDesc(Long zoneId, Pageable pageable);
    List<RiskEvent> findTop10ByZoneIdOrderByTimestampDesc(Long zoneId);
    List<RiskEvent> findByZoneVenueIdOrderByTimestampDesc(Long venueId, Pageable pageable);
    List<RiskEvent> findByZoneIdAndTimestampAfterOrderByTimestampAsc(Long zoneId, Instant timestamp);
    java.util.Optional<RiskEvent> findFirstByZoneIdAndSourceOrderByTimestampDesc(Long zoneId, com.nirikshan.model.RiskEventSource source);
    long deleteByTimestampBefore(Instant cutoff);
}
