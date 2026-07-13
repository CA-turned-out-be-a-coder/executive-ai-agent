package com.example.aiassistant;

import dev.langchain4j.service.TokenStream;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamingChatController {

    private final Assistant assistant;
    private final CurrentUserHolder currentUserHolder;
    private final ChatMemoryService chatMemoryService;

    public StreamingChatController(Assistant assistant, CurrentUserHolder currentUserHolder, ChatMemoryService chatMemoryService) {
        this.assistant = assistant;
        this.currentUserHolder = currentUserHolder;
        this.chatMemoryService = chatMemoryService;
    }

    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userId = null;
        if (principal instanceof OidcUser oidcUser) {
            currentUserHolder.setCurrentUser(oidcUser);
            userId = oidcUser.getSubject();
        }

        SseEmitter emitter = new SseEmitter();
        String finalUserId = userId;

        chatMemoryService.persistUserMessage(request.getConversationId(), finalUserId, request.getMessage());

        StringBuilder fullResponse = new StringBuilder();
        TokenStream tokenStream = assistant.chatStream(request.getConversationId(), request.getMessage());

        tokenStream
                .onPartialResponse(partialResponse -> {
                    try {
                        fullResponse.append(partialResponse);
                        emitter.send(partialResponse);
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                })
                .onCompleteResponse(completeResponse -> {
                    chatMemoryService.persistAiMessage(request.getConversationId(), fullResponse.toString());
                    emitter.complete();
                })
                .onError(emitter::completeWithError)
                .start();

        return emitter;
    }
}
