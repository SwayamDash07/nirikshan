package com.nirikshan.repository;

import com.nirikshan.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByStatusOrderByCreatedAtDesc(RecommendationStatus status);
    List<Recommendation> findAllByOrderByCreatedAtDesc();
    boolean existsByTypeAndZoneIdAndStatus(RecommendationType type, Long zoneId, RecommendationStatus status);
    boolean existsByTypeAndZoneIsNullAndStatus(RecommendationType type, RecommendationStatus status);
    boolean existsByTypeAndZoneIdAndCreatedAtAfter(RecommendationType type, Long zoneId, Instant createdAt);
    boolean existsByTypeAndZoneIsNullAndCreatedAtAfter(RecommendationType type, Instant createdAt);
    List<Recommendation> findByZoneIdAndStatus(Long zoneId, RecommendationStatus status);
    List<Recommendation> findByTypeAndStatus(RecommendationType type, RecommendationStatus status);
}
