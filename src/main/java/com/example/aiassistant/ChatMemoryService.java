package com.example.aiassistant;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatMemoryService {

    private final ChatMessageRepository repository;
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

    public ChatMemoryService(ChatMessageRepository repository) {
        this.repository = repository;
    }

    public ChatMemory getMemory(String conversationId) {
        return memories.computeIfAbsent(conversationId, this::loadMemory);
    }

    private ChatMemory loadMemory(String conversationId) {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ChatMessageEntity> stored = repository.findByConversationIdOrderByTimestampAsc(conversationId);

        if (stored.isEmpty()) {
            memory.add(SystemMessage.from(SYSTEM_PROMPT));
            repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.SYSTEM, SYSTEM_PROMPT));
        } else {
            for (ChatMessageEntity entity : stored) {
                switch (entity.getRole()) {
                    case SYSTEM -> memory.add(SystemMessage.from(entity.getContent()));
                    case USER -> memory.add(UserMessage.from(entity.getContent()));
                    case AI -> memory.add(AiMessage.from(entity.getContent()));
                }
            }
        }

        return memory;
    }

    public void addUserMessage(String conversationId, String content) {
        getMemory(conversationId).add(UserMessage.from(content));
        repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.USER, content));
    }

    public void addAiMessage(String conversationId, String content) {
        getMemory(conversationId).add(AiMessage.from(content));
        repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.AI, content));
    }

}