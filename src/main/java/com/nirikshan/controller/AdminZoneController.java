package com.nirikshan.controller;

import com.nirikshan.dto.AdminZoneResponse;
import com.nirikshan.service.ZoneFeedService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/zones")
public class AdminZoneController {
    private final ZoneFeedService feeds;

    public AdminZoneController(ZoneFeedService feeds) { this.feeds = feeds; }

    @GetMapping
    public List<AdminZoneResponse> list() { return feeds.list(); }

    @PostMapping(value = "/{zoneId}/connect-footage", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AdminZoneResponse connect(@PathVariable Long zoneId, @RequestParam("file") MultipartFile file) throws IOException {
        return feeds.connect(zoneId, file);
    }

    @PostMapping("/{zoneId}/stop-coverage")
    public AdminZoneResponse stop(@PathVariable Long zoneId) { return feeds.stop(zoneId); }
}
