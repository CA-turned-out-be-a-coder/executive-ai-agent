package com.example.aiassistant;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatMemoryService {

    private final Map<String, ChatMemory> memories = new ConcurrentHashMap<>();

    public ChatMemory getMemory(String conversationId) {
        return memories.computeIfAbsent(conversationId, id ->
                MessageWindowChatMemory.withMaxMessages(20));
    }

}