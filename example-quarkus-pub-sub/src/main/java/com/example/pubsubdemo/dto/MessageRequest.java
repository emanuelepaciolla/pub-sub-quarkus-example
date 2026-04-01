package com.example.pubsubdemo.dto;

import com.example.pubsubdemo.entity.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageRequest(
        @NotBlank(message = "Message text cannot be blank")
        @Size(max = 1000, message = "Message text cannot exceed 1000 characters")
        String text,

        String source,

        EventType eventType
) {
    public MessageRequest {
        source = (source == null || source.isBlank()) ? "API" : source;
        eventType = (eventType == null) ? EventType.DEMO : eventType;
    }
}
