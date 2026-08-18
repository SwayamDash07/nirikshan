package com.nirikshan.repository;

import com.nirikshan.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnnouncementDraftRepository extends JpaRepository<AnnouncementDraft, Long> {
    List<AnnouncementDraft> findAllByOrderByCreatedAtDesc();
    List<AnnouncementDraft> findByApprovalStatusAndSentFalseOrderByCreatedAtDesc(AnnouncementApprovalStatus status);
}
