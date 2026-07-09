package com.example.aiassistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

public interface Assistant {
    String chat(@MemoryId String conversationId, @UserMessage String message);
}