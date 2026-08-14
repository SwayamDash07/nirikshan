package com.nirikshan.repository;

import com.nirikshan.model.ZoneFeed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ZoneFeedRepository extends JpaRepository<ZoneFeed, Long> {
    Optional<ZoneFeed> findByZone_Id(Long zoneId);
}
