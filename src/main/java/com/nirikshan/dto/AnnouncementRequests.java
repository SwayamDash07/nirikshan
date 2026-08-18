package com.nirikshan.dto;

import com.nirikshan.model.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AnnouncementRequests {
    private AnnouncementRequests() { }
    public record Draft(Long targetZoneId, @NotBlank @Size(max = 2000) String englishText,
                        @NotBlank @Size(max = 2000) String hindiText, @NotBlank @Size(max = 2000) String odiaText,
                        RiskLevel urgency) { }
}
