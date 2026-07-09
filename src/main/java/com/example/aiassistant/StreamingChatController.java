package com.example.aiassistant;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamingChatController {

    private final StreamingChatModel streamingChatModel;
    private final ChatMemoryService chatMemoryService;

    public StreamingChatController(StreamingChatModel streamingChatModel, ChatMemoryService chatMemoryService) {
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryService = chatMemoryService;
    }

    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter();

        chatMemoryService.addUserMessage(request.getConversationId(), request.getMessage());
        ChatMemory memory = chatMemoryService.getMemory(request.getConversationId());

        streamingChatModel.chat(memory.messages(), new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                try {
                    emitter.send(partialResponse);
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                chatMemoryService.addAiMessage(request.getConversationId(), completeResponse.aiMessage().text());
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                emitter.completeWithError(error);
            }
        });

        return emitter;
    }

}