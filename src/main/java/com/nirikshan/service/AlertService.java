package com.nirikshan.service;

import com.nirikshan.dto.AlertResponse;
import com.nirikshan.model.Alert;
import com.nirikshan.repository.AlertRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class AlertService {
    private final AlertRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
    public AlertService(AlertRepository repository, SimpMessagingTemplate messagingTemplate) { this.repository = repository; this.messagingTemplate = messagingTemplate; }
    @Transactional(readOnly = true) public List<AlertResponse> list(Boolean active) {
        List<Alert> alerts = active != null && active ? repository.findByResolvedOrderByTimestampDesc(false) : repository.findAllByOrderByTimestampDesc();
        return alerts.stream().map(this::toResponse).toList();
    }
    @Transactional public AlertResponse resolve(Long id) {
        Alert alert = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Alert", id));
        alert.setResolved(true); alert.setResolvedAt(Instant.now());
        AlertResponse response = toResponse(repository.save(alert));
        messagingTemplate.convertAndSend("/topic/alerts", response);
        return response;
    }
    private AlertResponse toResponse(Alert a) { return new AlertResponse(a.getId(), a.getZone().getId(), a.getZone().getName(), a.getTimestamp(), a.getMessage(), a.getSeverity(), a.isResolved(), a.getResolvedAt()); }
}
