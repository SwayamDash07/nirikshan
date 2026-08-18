package com.nirikshan.service;

import com.nirikshan.model.AnnouncementApprovalStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementWorkflowTest {
    @Test void deliveryRequiresApprovalAndPreventsRepeatSend() {
        assertFalse(AnnouncementWorkflow.canSend(AnnouncementApprovalStatus.PENDING_APPROVAL, false));
        assertFalse(AnnouncementWorkflow.canSend(AnnouncementApprovalStatus.REJECTED, false));
        assertTrue(AnnouncementWorkflow.canSend(AnnouncementApprovalStatus.APPROVED, false));
        assertFalse(AnnouncementWorkflow.canSend(AnnouncementApprovalStatus.APPROVED, true));
    }
}
