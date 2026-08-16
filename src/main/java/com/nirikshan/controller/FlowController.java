package com.nirikshan.controller;

import com.nirikshan.dto.FlowStatusResponse;
import com.nirikshan.model.UserRole;
import com.nirikshan.security.CurrentUser;
import com.nirikshan.service.FlowStatusService;
import org.springframework.web.bind.annotation.*;

@RestController
public class FlowController {
    private final FlowStatusService flow;
    private final CurrentUser current;
    public FlowController(FlowStatusService flow, CurrentUser current) { this.flow = flow; this.current = current; }

    @GetMapping("/api/zones/{zoneId}/flow-status")
    public FlowStatusResponse status(@PathVariable Long zoneId) {
        var user = current.get();
        if (user.getRole() == UserRole.SECURITY && (user.getAssignedZone() == null || !user.getAssignedZone().getId().equals(zoneId))) {
            throw new IllegalArgumentException("Flow status is outside your assigned zone");
        }
        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.SECURITY) {
            throw new IllegalArgumentException("Detailed flow status is not available to citizen accounts");
        }
        return flow.get(zoneId);
    }
}
