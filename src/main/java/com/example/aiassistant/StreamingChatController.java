package com.example.aiassistant;

import dev.langchain4j.service.TokenStream;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@RestController
public class StreamingChatController {

    private final Assistant assistant;
    private final CurrentUserHolder currentUserHolder;
    private final ChatMemoryService chatMemoryService;

    private static final Map<String, String> TOOL_BADGES = Map.ofEntries(
            Map.entry("getUpcomingEvents", "📅 Calendar checked"),
            Map.entry("createCalendarEvent", "📅 Calendar updated"),
            Map.entry("updateCalendarEvent", "📅 Calendar updated"),
            Map.entry("deleteCalendarEvent", "📅 Calendar updated"),
            Map.entry("getRecentEmails", "📧 Inbox checked"),
            Map.entry("searchEmails", "📧 Inbox checked"),
            Map.entry("readEmail", "📧 Inbox checked"),
            Map.entry("createEmailDraft", "📧 Draft created"),
            Map.entry("sendEmail", "📧 Email sent"),
            Map.entry("getWeather", "🌤️ Weather checked"),
            Map.entry("getStockQuote", "📈 Stock checked"),
            Map.entry("searchWeb", "🔍 Web searched")
    );

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
        Set<String> badgesUsed = new LinkedHashSet<>();
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
                .onToolExecuted(toolExecution -> {
                    String toolName = toolExecution.request().name();
                    String badge = TOOL_BADGES.get(toolName);
                    if (badge != null) {
                        badgesUsed.add(badge);
                    }
                })
                .onCompleteResponse(completeResponse -> {
                    chatMemoryService.persistAiMessage(request.getConversationId(), fullResponse.toString());
                    try {
                        if (!badgesUsed.isEmpty()) {
                            emitter.send(SseEmitter.event().name("tools").data(String.join(",", badgesUsed)));
                        }
                    } catch (Exception e) {
                        // non-fatal, badges are a nice-to-have, don't fail the whole response over it
                    }
                    emitter.complete();
                })
                .onError(emitter::completeWithError)
                .start();

        return emitter;
    }
}
