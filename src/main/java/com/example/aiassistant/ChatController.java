package com.example.aiassistant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final Assistant assistant;
    private final ChatMemoryService chatMemoryService;

    public ChatController(Assistant assistant, ChatMemoryService chatMemoryService) {
        this.assistant = assistant;
        this.chatMemoryService = chatMemoryService;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        chatMemoryService.getMemory(request.getConversationId()); // ensures memory is loaded/seeded

        String response = assistant.chat(request.getConversationId(), request.getMessage());

        chatMemoryService.persistUserMessage(request.getConversationId(), request.getMessage());
        chatMemoryService.persistAiMessage(request.getConversationId(), response);

        return response;
    }
}