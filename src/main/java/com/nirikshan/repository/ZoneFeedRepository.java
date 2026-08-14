package com.nirikshan.repository;

import com.nirikshan.model.ZoneFeed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface ZoneFeedRepository extends JpaRepository<ZoneFeed, Long> {
    Optional<ZoneFeed> findByZone_Id(Long zoneId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "zone")
    List<ZoneFeed> findByStatus(com.nirikshan.model.ZoneFeedStatus status);
}
