package com.nirikshan.service;

import com.nirikshan.dto.AnnouncementRequests.Draft;
import com.nirikshan.dto.AnnouncementResponse;
import com.nirikshan.model.*;
import com.nirikshan.repository.AnnouncementDraftRepository;
import com.nirikshan.repository.ZoneRepository;
import com.nirikshan.security.CurrentUser;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class AnnouncementService {
    private final AnnouncementDraftRepository drafts;
    private final ZoneRepository zones;
    private final CurrentUser current;
    private final SimpMessagingTemplate messaging;

    public AnnouncementService(AnnouncementDraftRepository drafts, ZoneRepository zones, CurrentUser current,
                               SimpMessagingTemplate messaging) {
        this.drafts = drafts; this.zones = zones; this.current = current; this.messaging = messaging;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> list() { return drafts.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList(); }

    @Transactional
    public AnnouncementResponse create(Draft request) {
        Zone zone = request.targetZoneId() == null ? null : zones.findById(request.targetZoneId()).orElseThrow(() -> new IllegalArgumentException("Zone not found"));
        AnnouncementDraft draft = base(zone, request.englishText(), request.hindiText(), request.odiaText(),
                request.urgency() == null ? RiskLevel.MEDIUM : request.urgency(), RiskEventSource.LIVE);
        return broadcast(drafts.save(draft));
    }

    @Transactional
    public AnnouncementResponse createSuggested(Zone zone, RiskLevel urgency, RiskEventSource source, String action) {
        String name = zone == null ? "the venue" : zone.getName();
        String english = "Safety update for " + name + ": " + action + ". Please follow staff directions and use marked routes. Do not move against crowd flow.";
        String hindi = name + " के लिए सुरक्षा सूचना: " + action + "। कृपया सुरक्षा कर्मियों के निर्देशों का पालन करें और चिन्हित मार्ग का उपयोग करें। भीड़ के प्रवाह के विपरीत न चलें।";
        String odia = name + " ପାଇଁ ସୁରକ୍ଷା ସୂଚନା: " + action + "। ଦୟାକରି ସୁରକ୍ଷା କର୍ମୀଙ୍କ ନିର୍ଦ୍ଦେଶ ମାନନ୍ତୁ ଏବଂ ଚିହ୍ନିତ ପଥ ବ୍ୟବହାର କରନ୍ତୁ। ଭିଡ଼ର ପ୍ରବାହ ବିପରୀତ ଦିଗରେ ଚାଲନ୍ତୁ ନାହିଁ।";
        return broadcast(drafts.save(base(zone, english, hindi, odia, urgency, source)));
    }

    @Transactional
    public AnnouncementResponse approve(Long id) {
        AnnouncementDraft draft = find(id);
        if (draft.isSent()) throw new IllegalStateException("A sent announcement cannot be changed");
        draft.setApprovalStatus(AnnouncementApprovalStatus.APPROVED);
        draft.setApprovedByUser(current.get());
        return broadcast(draft);
    }

    @Transactional
    public AnnouncementResponse reject(Long id) {
        AnnouncementDraft draft = find(id);
        if (draft.isSent()) throw new IllegalStateException("A sent announcement cannot be rejected");
        draft.setApprovalStatus(AnnouncementApprovalStatus.REJECTED);
        return broadcast(draft);
    }

    @Transactional
    public AnnouncementResponse send(Long id) {
        AnnouncementDraft draft = find(id);
        if (!AnnouncementWorkflow.canSend(draft.getApprovalStatus(), draft.isSent())) {
            throw new IllegalStateException("Announcement must be approved and unsent before delivery");
        }
        draft.setSent(true); draft.setSentAt(Instant.now());
        AnnouncementResponse response = broadcast(draft);
        messaging.convertAndSend("/topic/citizen-announcements", response);
        return response;
    }

    private AnnouncementDraft base(Zone zone, String english, String hindi, String odia, RiskLevel urgency, RiskEventSource source) {
        AnnouncementDraft draft = new AnnouncementDraft();
        draft.setTargetZone(zone); draft.setEnglishText(english); draft.setHindiText(hindi); draft.setOdiaText(odia);
        draft.setUrgency(urgency); draft.setSource(source);
        return draft;
    }
    private AnnouncementDraft find(Long id) { return drafts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Announcement draft", id)); }
    private AnnouncementResponse broadcast(AnnouncementDraft draft) { AnnouncementResponse response = response(draft); messaging.convertAndSend("/topic/announcements", response); return response; }
    private AnnouncementResponse response(AnnouncementDraft draft) { Zone zone = draft.getTargetZone(); User user = draft.getApprovedByUser();
        return new AnnouncementResponse(draft.getId(), zone == null ? null : zone.getId(), zone == null ? null : zone.getName(),
                draft.getEnglishText(), draft.getHindiText(), draft.getOdiaText(), draft.getUrgency(), draft.getSource(),
                draft.getApprovalStatus(), draft.isSent(), draft.getSentAt(), user == null ? null : user.getId(), draft.getCreatedAt()); }
}
