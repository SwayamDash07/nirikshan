package com.nirikshan.controller;
import com.nirikshan.dto.AlertResponse;
import com.nirikshan.service.AlertService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/alerts")
public class AlertController {
    private final AlertService service;
    public AlertController(AlertService service) { this.service = service; }
    @GetMapping public List<AlertResponse> list(@RequestParam(required = false) Boolean active) { return service.list(active); }
    @PatchMapping("/{id}/resolve") public AlertResponse resolve(@PathVariable Long id) { return service.resolve(id); }
    @PatchMapping("/resolve-all") public List<AlertResponse> resolveAll() { return service.resolveAll(); }
}
