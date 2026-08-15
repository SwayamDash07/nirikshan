package com.nirikshan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AssistantChatRequest(@NotBlank @Size(max = 1200) String message, Long zoneId,
                                   @Size(max = 10) List<@Valid HistoryMessage> conversationHistory) {
    public record HistoryMessage(@NotBlank @Size(max = 20) String role, @NotBlank @Size(max = 800) String content) { }
}
