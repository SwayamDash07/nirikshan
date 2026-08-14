package com.nirikshan.repository;
import com.nirikshan.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findByVenueId(Long venueId);
    boolean existsByVenueId(Long venueId);
    boolean existsByVenueIdAndNameIgnoreCase(Long venueId, String name);
}
