package com.nirikshan.service;

import com.nirikshan.dto.AlertResponse;
import com.nirikshan.model.Alert;
import com.nirikshan.repository.AlertRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AlertService {
    private final AlertRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
    public AlertService(AlertRepository repository, SimpMessagingTemplate messagingTemplate) { this.repository = repository; this.messagingTemplate = messagingTemplate; }
    @Transactional(readOnly = true) public List<AlertResponse> list(Boolean active) {
        List<Alert> alerts = active != null && active ? repository.findByResolvedOrderByTimestampDesc(false) : repository.findAllByOrderByTimestampDesc();
        if (active != null && active) {
            Map<Long, Alert> latestByZone = new LinkedHashMap<>();
            alerts.forEach(alert -> latestByZone.putIfAbsent(alert.getZone().getId(), alert));
            return latestByZone.values().stream().map(this::toResponse).toList();
        }
        return alerts.stream().map(this::toResponse).toList();
    }
    @Transactional public AlertResponse resolve(Long id) {
        Alert alert = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Alert", id));
        alert.setResolved(true); alert.setResolvedAt(Instant.now());
        AlertResponse response = toResponse(repository.save(alert));
        messagingTemplate.convertAndSend("/topic/alerts", response);
        return response;
    }
    @Transactional public List<AlertResponse> resolveAll() {
        List<Alert> active = repository.findByResolvedOrderByTimestampDesc(false);
        Instant resolvedAt = Instant.now();
        List<AlertResponse> responses = active.stream().map(alert -> {
            alert.setResolved(true); alert.setResolvedAt(resolvedAt);
            AlertResponse response = toResponse(repository.save(alert));
            messagingTemplate.convertAndSend("/topic/alerts", response);
            return response;
        }).toList();
        return responses;
    }
    private AlertResponse toResponse(Alert a) { return new AlertResponse(a.getId(), a.getZone().getId(), a.getZone().getName(), a.getTimestamp(), a.getMessage(), a.getSeverity(), a.isResolved(), a.getResolvedAt(), a.getSource()); }
}
