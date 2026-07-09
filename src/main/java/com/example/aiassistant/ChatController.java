package com.example.aiassistant;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private final ChatMemoryService chatMemoryService;

    public ChatController(ChatMemoryService chatMemoryService) {
        this.chatMemoryService = chatMemoryService;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        ChatModel model = OpenAiChatModel.builder()
                .apiKey(openaiApiKey)
                .modelName("gpt-4o-mini")
                .build();

        ChatMemory memory = chatMemoryService.getMemory(request.getConversationId());

        memory.add(UserMessage.from(request.getMessage()));

        AiMessage response = model.chat(memory.messages()).aiMessage();

        memory.add(response);

        return response.text();
    }

}
