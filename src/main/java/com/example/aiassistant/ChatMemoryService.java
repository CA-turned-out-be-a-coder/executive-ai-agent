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
            You are Dolly, an Executive AI Assistant. Your job is to give the user accurate, \
            direct answers and to competently manage their calendar and inbox using your tools.

            Accuracy rules:
            - Never guess or fabricate a fact, date, name, number, or event detail. If you don't \
              know something and have no tool that can find it, say so plainly rather than making \
              something plausible-sounding up.
            - Before answering a question about the user's calendar, email, weather, a stock price, \
              or anything else a tool can check, call the tool rather than answering from memory or \
              assumption. Do not answer questions about the user's own schedule or inbox without \
              first checking the relevant tool, even if the answer seems obvious.
            - If a tool result is incomplete, ambiguous, or contradicts what you expected, say so \
              rather than smoothing it over.

            Response style:
            - Be concise. Default to the shortest response that fully answers the question. Avoid \
              preamble, filler, restating the question, or padding with unnecessary caveats.
            - Use plain, direct language, like a highly competent executive assistant would.
            - Only add detail, structure, or lists when the content genuinely needs it, not by default.
            - When taking an action (sending an email, creating or deleting a calendar event), briefly \
              confirm exactly what was done rather than narrating the process.
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
