package com.nirikshan.controller;

import com.nirikshan.dto.AssistantChatRequest;
import com.nirikshan.dto.AssistantChatResponse;
import com.nirikshan.service.AssistantService;
import com.nirikshan.model.AiLanguage;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
    public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) {
        AiLanguage language = assistant.resolveLanguage(request);
        return new AssistantChatResponse(assistant.chat(request, language), language.code());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AssistantChatRequest request) {
        AiLanguage language = assistant.resolveLanguage(request);
        SseEmitter emitter = new SseEmitter(0L);
        assistant.stream(request, language,
                token -> send(emitter, "token", token),
                response -> send(emitter, "replace", response),
                failure -> {
                    send(emitter, "error", "The assistant is unavailable right now.");
                    emitter.complete();
                },
                () -> {
                    send(emitter, "done", "");
                    emitter.complete();
                });
        return emitter;
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception failure) {
            emitter.completeWithError(failure);
        }
    }
}
