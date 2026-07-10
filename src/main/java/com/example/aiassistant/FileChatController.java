package com.example.aiassistant;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
public class FileChatController {

    private final StreamingChatModel streamingChatModel;
    private final ChatMemoryService chatMemoryService;
    private final FileExtractionService fileExtractionService;

    public FileChatController(StreamingChatModel streamingChatModel, ChatMemoryService chatMemoryService,
                               FileExtractionService fileExtractionService) {
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryService = chatMemoryService;
        this.fileExtractionService = fileExtractionService;
    }

    @PostMapping("/chat/file/stream")
    public SseEmitter chatWithFile(@RequestBody FileChatRequest request) {
        SseEmitter emitter = new SseEmitter();

        String userText = (request.getMessage() == null || request.getMessage().isBlank())
                ? defaultPromptFor(request.getFileMimeType())
                : request.getMessage();

        UserMessage userMessage;
        String memoryLabel;

        try {
            if (request.getFileMimeType().startsWith("image/")) {
                userMessage = UserMessage.from(
                        TextContent.from(userText),
                        ImageContent.from(request.getFileBase64(), request.getFileMimeType())
                );
                memoryLabel = "[Image attached] " + userText;
            } else {
                String extractedText = fileExtractionService.extractText(
                        request.getFileBase64(), request.getFileMimeType(), request.getFileName());
                String combined = userText + "\n\n--- Attached document content ---\n" + extractedText;
                userMessage = UserMessage.from(TextContent.from(combined));
                memoryLabel = "[File attached: " + request.getFileName() + "] " + userText;
            }
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        StringBuilder fullResponse = new StringBuilder();
        String finalMemoryLabel = memoryLabel;

        streamingChatModel.chat(List.of(userMessage), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                try {
                    fullResponse.append(partialResponse);
                    emitter.send(partialResponse);
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                chatMemoryService.persistUserMessage(request.getConversationId(), finalMemoryLabel);
                chatMemoryService.persistAiMessage(request.getConversationId(), fullResponse.toString());
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                emitter.completeWithError(error);
            }
        });

        return emitter;
    }

    private String defaultPromptFor(String mimeType) {
        if (mimeType.startsWith("image/")) {
            return "What do you see in this image?";
        }
        return "Please summarize this document.";
    }
}
