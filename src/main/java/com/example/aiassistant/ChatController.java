package com.example.aiassistant;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        ChatModel model = OpenAiChatModel.builder()
                .apiKey(openaiApiKey)
                .modelName("gpt-4o-mini")
                .build();

        return model.chat(message);
    }

}