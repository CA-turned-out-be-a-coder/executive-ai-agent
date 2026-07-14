package com.example.aiassistant;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ConversationController(ConversationRepository conversationRepository, ChatMessageRepository chatMessageRepository) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    private String getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser.getSubject();
        }
        throw new IllegalStateException("No authenticated user found");
    }

    @GetMapping
    public List<Map<String, Object>> listConversations() {
        String userId = getCurrentUserId();
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(c -> Map.<String, Object>of(
                        "id", c.getId(),
                        "title", c.getTitle(),
                        "updatedAt", c.getUpdatedAt().toString()
                ))
                .collect(Collectors.toList());
    }

    @PostMapping
    public Map<String, String> createConversation() {
        String userId = getCurrentUserId();
        String id = UUID.randomUUID().toString();
        ConversationEntity conversation = new ConversationEntity(id, userId, "New chat");
        conversationRepository.save(conversation);
        return Map.of("id", id, "title", "New chat");
    }

    @GetMapping("/{id}/messages")
    public List<Map<String, String>> getConversationMessages(@PathVariable String id) {
        String userId = getCurrentUserId();
        if (!conversationRepository.existsByIdAndUserId(id, userId)) {
            throw new IllegalArgumentException("Conversation not found");
        }

        return chatMessageRepository.findByConversationIdOrderByTimestampAsc(id).stream()
                .filter(m -> m.getRole() != ChatMessageEntity.MessageRole.SYSTEM)
                .map(m -> Map.of(
                        "role", m.getRole() == ChatMessageEntity.MessageRole.USER ? "user" : "assistant",
                        "content", m.getContent()
                ))
                .collect(Collectors.toList());
    }

    @DeleteMapping("/{id}")
    public void deleteConversation(@PathVariable String id) {
        String userId = getCurrentUserId();
        if (!conversationRepository.existsByIdAndUserId(id, userId)) {
            throw new IllegalArgumentException("Conversation not found");
        }
        conversationRepository.deleteById(id);
    }

    @DeleteMapping
    public void deleteAllConversations() {
        String userId = getCurrentUserId();
        List<ConversationEntity> conversations = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        conversationRepository.deleteAll(conversations);
    }
}
