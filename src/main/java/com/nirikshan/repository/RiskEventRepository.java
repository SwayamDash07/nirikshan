package com.nirikshan.repository;
import com.nirikshan.model.RiskEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RiskEventRepository extends JpaRepository<RiskEvent, Long> {
    List<RiskEvent> findByZoneIdOrderByTimestampDesc(Long zoneId, Pageable pageable);
}
