package com.example.aiassistant;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final Assistant assistant;
    private final ChatMemoryService chatMemoryService;
    private final CurrentUserHolder currentUserHolder;

    public ChatController(Assistant assistant, ChatMemoryService chatMemoryService, CurrentUserHolder currentUserHolder) {
        this.assistant = assistant;
        this.chatMemoryService = chatMemoryService;
        this.currentUserHolder = currentUserHolder;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            currentUserHolder.setCurrentUser(oidcUser);
        }

        chatMemoryService.getMemory(request.getConversationId());

        String response = assistant.chat(request.getConversationId(), request.getMessage());

        chatMemoryService.persistUserMessage(request.getConversationId(), request.getMessage());
        chatMemoryService.persistAiMessage(request.getConversationId(), response);

        return response;
    }
}
