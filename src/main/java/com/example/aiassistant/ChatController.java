package com.example.aiassistant;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatModel chatModel;
    private final ChatMemoryService chatMemoryService;

    public ChatController(ChatModel chatModel, ChatMemoryService chatMemoryService) {
        this.chatModel = chatModel;
        this.chatMemoryService = chatMemoryService;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        chatMemoryService.addUserMessage(request.getConversationId(), request.getMessage());

        ChatMemory memory = chatMemoryService.getMemory(request.getConversationId());
        AiMessage response = chatModel.chat(memory.messages()).aiMessage();

        chatMemoryService.addAiMessage(request.getConversationId(), response.text());

        return response.text();
    }

}