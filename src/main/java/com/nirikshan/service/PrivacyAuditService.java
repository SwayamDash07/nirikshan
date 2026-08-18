package com.nirikshan.service;

import com.nirikshan.model.AuditEvent;
import com.nirikshan.repository.AuditEventRepository;
import com.nirikshan.security.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class PrivacyAuditService {
    private final AuditEventRepository events;
    private final CurrentUser current;
    public PrivacyAuditService(AuditEventRepository events, CurrentUser current) { this.events = events; this.current = current; }
    public void record(String action, String resourceType, Long resourceId, String details) {
        Long actor = null;
        try { actor = current.get().getId(); } catch (RuntimeException ignored) { }
        events.save(new AuditEvent(action, resourceType, resourceId, actor, safeDetails(details)));
    }
    private String safeDetails(String details) {
        if (details == null) return null;
        String safe = details.replaceAll("(?i)bearer\\s+[A-Za-z0-9._-]+", "Bearer [redacted]")
                .replaceAll("(?i)(token|password|secret)\\s*[=:]\\s*[^,; ]+", "$1=[redacted]");
        return safe.substring(0, Math.min(500, safe.length()));
    }
}
