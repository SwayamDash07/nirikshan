package com.nirikshan.service;

import com.nirikshan.model.AnnouncementApprovalStatus;

/** Pure state guard, intentionally separate from messaging and persistence. */
public final class AnnouncementWorkflow {
    private AnnouncementWorkflow() { }
    public static boolean canSend(AnnouncementApprovalStatus status, boolean sent) {
        return status == AnnouncementApprovalStatus.APPROVED && !sent;
    }
}
