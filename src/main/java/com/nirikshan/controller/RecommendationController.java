package com.nirikshan.controller;

import com.nirikshan.dto.RecommendationResponse;
import com.nirikshan.service.RecommendationService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService service;
    public RecommendationController(RecommendationService service) { this.service = service; }
    @GetMapping public List<RecommendationResponse> list(@RequestParam(required = false) Boolean active) { return service.list(active); }
    @GetMapping("/customer") public List<RecommendationResponse> customerList(@RequestParam(required = false) Boolean active) { return service.customerList(active); }
    @PatchMapping("/{id}/acknowledge") public RecommendationResponse acknowledge(@PathVariable Long id) { return service.acknowledge(id); }
    @PatchMapping("/{id}/dismiss") public RecommendationResponse dismiss(@PathVariable Long id) { return service.dismiss(id); }
}
