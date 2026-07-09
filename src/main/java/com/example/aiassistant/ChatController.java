package com.example.aiassistant;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
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
        ChatMemory memory = chatMemoryService.getMemory(request.getConversationId());

        memory.add(UserMessage.from(request.getMessage()));

        AiMessage response = chatModel.chat(memory.messages()).aiMessage();

        memory.add(response);

        return response.text();
    }

}