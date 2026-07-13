package com.example.aiassistant;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatMemoryService {

    private final ChatMessageRepository repository;
    private final ConversationRepository conversationRepository;
    private final Map<String, ChatMemory> memories = new ConcurrentHashMap<>();

    private static final String SYSTEM_PROMPT = """
            You are an Executive AI Assistant. Your role is to help the user manage \
            their work efficiently: answering questions, summarizing information, and \
            assisting with planning and organization. You have access to tools that let \
            you check the user's Google Calendar and Gmail inbox when relevant.

            Guidelines:
            - Be concise and professional, like a highly competent executive assistant.
            - If you are unsure about something, say so clearly instead of guessing.
            - When asked for factual information you cannot verify, state that clearly.
            - Keep responses focused and avoid unnecessary filler.
            - Use the calendar or email tools when the user asks about their schedule or messages.
            """;

    public ChatMemoryService(ChatMessageRepository repository, ConversationRepository conversationRepository) {
        this.repository = repository;
        this.conversationRepository = conversationRepository;
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

    private void touchConversation(String conversationId, String userId, String firstUserMessage) {
        ConversationEntity conversation = conversationRepository.findById(conversationId).orElse(null);

        if (conversation == null) {
            String title = deriveTitle(firstUserMessage);
            conversation = new ConversationEntity(conversationId, userId, title);
            conversationRepository.save(conversation);
        } else {
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conversation);
        }
    }

    private String deriveTitle(String message) {
        if (message == null || message.isBlank()) {
            return "New chat";
        }
        String trimmed = message.trim();
        return trimmed.length() > 48 ? trimmed.substring(0, 48) + "…" : trimmed;
    }

    public void addUserMessage(String conversationId, String content) {
        getMemory(conversationId).add(UserMessage.from(content));
        repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.USER, content));
    }

    public void addAiMessage(String conversationId, String content) {
        getMemory(conversationId).add(AiMessage.from(content));
        repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.AI, content));
    }

    public void persistUserMessage(String conversationId, String userId, String content) {
        repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.USER, content));
        touchConversation(conversationId, userId, content);
    }

    public void persistAiMessage(String conversationId, String content) {
        repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.AI, content));
    }
}
