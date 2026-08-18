package com.nirikshan.controller;

import com.nirikshan.dto.AnnouncementRequests.Draft;
import com.nirikshan.dto.AnnouncementResponse;
import com.nirikshan.service.AnnouncementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final AnnouncementService service;
    public AnnouncementController(AnnouncementService service) { this.service = service; }
    @GetMapping public List<AnnouncementResponse> list() { return service.list(); }
    @PostMapping public AnnouncementResponse create(@Valid @RequestBody Draft request) { return service.create(request); }
    @PatchMapping("/{id}/approve") public AnnouncementResponse approve(@PathVariable Long id) { return service.approve(id); }
    @PatchMapping("/{id}/reject") public AnnouncementResponse reject(@PathVariable Long id) { return service.reject(id); }
    @PostMapping("/{id}/send") public AnnouncementResponse send(@PathVariable Long id) { return service.send(id); }
}
