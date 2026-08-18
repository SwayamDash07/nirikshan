package com.nirikshan.dto;

import com.nirikshan.model.*;
import java.time.Instant;

public record AnnouncementResponse(Long id, Long targetZoneId, String targetZoneName,
                                   String englishText, String hindiText, String odiaText,
                                   RiskLevel urgency, RiskEventSource source,
                                   AnnouncementApprovalStatus approvalStatus, boolean sent, Instant sentAt,
                                   Long approvedByUserId, Instant createdAt) { }
