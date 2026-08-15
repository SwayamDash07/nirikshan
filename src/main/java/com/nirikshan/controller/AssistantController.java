package com.nirikshan.controller;

import com.nirikshan.dto.AssistantChatRequest;
import com.nirikshan.dto.AssistantChatResponse;
import com.nirikshan.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService assistant;
    public AssistantController(AssistantService assistant) { this.assistant = assistant; }
    @PostMapping("/chat")
    public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) { return new AssistantChatResponse(assistant.chat(request)); }
}
