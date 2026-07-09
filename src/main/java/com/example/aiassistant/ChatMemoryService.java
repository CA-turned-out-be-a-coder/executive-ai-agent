package com.example.aiassistant;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatMemoryService {

    private final Map<String, ChatMemory> memories = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT = """
            You are an Executive AI Assistant. Your role is to help the user manage \
            their work efficiently: answering questions, summarizing information, and \
            assisting with planning and organization.

            Guidelines:
            - Be concise and professional, like a highly competent executive assistant.
            - If you are unsure about something, say so clearly instead of guessing.
            - When asked for factual information you cannot verify, state that clearly.
            - Keep responses focused and avoid unnecessary filler.
            """;

    public ChatMemory getMemory(String conversationId) {
        return memories.computeIfAbsent(conversationId, id -> {
            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
            memory.add(SystemMessage.from(SYSTEM_PROMPT));
            return memory;
        });
    }

}
