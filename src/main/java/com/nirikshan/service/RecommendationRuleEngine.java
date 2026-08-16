package com.nirikshan.service;

import com.nirikshan.model.RecommendationType;
import com.nirikshan.model.RiskLevel;

/** Pure, auditable action-selection rules. No AI or database state is used here. */
public final class RecommendationRuleEngine {
    private RecommendationRuleEngine() { }
    public static Decision choose(RiskLevel risk, double densityDelta, double movementSlowdown,
                                  boolean persistentHotspot, boolean noSecurityAssignment) {
        RecommendationType type = persistentHotspot ? RecommendationType.CLOSE_ENTRY
                : densityDelta >= 0.35 ? RecommendationType.REDIRECT
                : risk.ordinal() >= RiskLevel.HIGH.ordinal() && movementSlowdown >= 0.20 ? RecommendationType.DEPLOY_SECURITY
                : risk.ordinal() >= RiskLevel.HIGH.ordinal() && noSecurityAssignment ? RecommendationType.REASSIGN_PERSONNEL
                : RecommendationType.OPEN_ROUTE;
        return new Decision(type, risk.ordinal() >= RiskLevel.HIGH.ordinal() ? risk : RiskLevel.MEDIUM);
    }
    public static boolean venueAnnouncementRequired(int sustainedHighZones) { return sustainedHighZones >= 2; }
    public record Decision(RecommendationType type, RiskLevel severity) { }
}
