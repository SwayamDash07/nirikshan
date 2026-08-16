package com.nirikshan.service;

import com.nirikshan.model.RecommendationType;
import com.nirikshan.model.RiskLevel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationRuleEngineTest {
    @Test void risingDensityRedirects() { assertEquals(RecommendationType.REDIRECT, RecommendationRuleEngine.choose(RiskLevel.MEDIUM, .40, 0, false, false).type()); }
    @Test void persistentHotspotAndSlowdownClosesEntry() { assertEquals(RecommendationType.CLOSE_ENTRY, RecommendationRuleEngine.choose(RiskLevel.HIGH, 0, .30, true, false).type()); }
    @Test void highRiskAndSlowdownDeploysSecurity() { assertEquals(RecommendationType.DEPLOY_SECURITY, RecommendationRuleEngine.choose(RiskLevel.HIGH, 0, .30, false, false).type()); }
    @Test void highRiskWithoutAssignmentReassignsPersonnel() { assertEquals(RecommendationType.REASSIGN_PERSONNEL, RecommendationRuleEngine.choose(RiskLevel.HIGH, 0, 0, false, true).type()); }
    @Test void sustainedMediumOpensRoute() { assertEquals(RecommendationType.OPEN_ROUTE, RecommendationRuleEngine.choose(RiskLevel.MEDIUM, 0, 0, false, false).type()); }
    @Test void twoHighZonesTriggerAnnouncement() { assertTrue(RecommendationRuleEngine.venueAnnouncementRequired(2)); }
}
