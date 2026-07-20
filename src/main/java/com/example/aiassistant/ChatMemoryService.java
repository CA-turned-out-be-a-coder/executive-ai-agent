package com.example.aiassistant;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatMemoryService {

    private final ChatMessageRepository repository;
    private final ConversationRepository conversationRepository;
    private final ChatModel chatModel;
    private final Map<String, ChatMemory> memories = new ConcurrentHashMap<>();

    private static String buildSystemPrompt() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
        String year = String.valueOf(LocalDate.now().getYear());

        return """
                You are Dolly, an Executive AI Assistant. Your job is to give the user accurate, \
                direct answers and to competently manage their calendar and inbox using your tools.

                Today's date is %s. Use this to correctly interpret words like "today", "this week", \
                "recent", or "latest".

                CRITICAL rule for web search: you must always put a specific year in your search query \
                text itself when asking about the most recent occurrence of anything that happens \
                periodically (an annual event, a champion, a title-holder, a position, a version number, \
                etc). Never search a bare term like "Super Bowl winner" or "who is the CEO" without a \
                year attached. Always search e.g. "Super Bowl %s winner" or "CEO of X %s". If the event \
                for the current year hasn't happened yet or returns nothing, try the previous year next.

                Accuracy rules:
                - Never guess or fabricate a fact, date, name, number, or event detail. If you don't \
                  know something and have no tool that can find it, say so plainly rather than making \
                  something plausible-sounding up.
                - Before answering a question about the user's calendar, email, weather, a stock price, \
                  or anything else a tool can check, call the tool rather than answering from memory or \
                  assumption. Do not answer questions about the user's own schedule or inbox without \
                  first checking the relevant tool, even if the answer seems obvious.
                - For anything recent, current, or time-sensitive, use the web search tool with recent=true \
                  and always include the current year (%s) in your query, per the rule above. Do not rely \
                  on your own training knowledge for current events, current title-holders, or anything \
                  that changes over time.
                - If a tool result is incomplete, ambiguous, or contradicts what you expected, say so \
                  rather than smoothing it over.

                Attribution rules (mandatory, not optional):
                - Every single time you report a result from getWeather, getStockQuote, getUpcomingEvents, \
                  getRecentEmails, searchEmails, or readEmail, you MUST begin that part of your answer with \
                  an explicit attribution phrase. Do not simply state the raw fact. For example, use "Per \
                  today's weather check, it's 77°F..." instead of just "It's 77°F...". Use "Your calendar \
                  shows..." instead of just listing events. Use "Checking your inbox, I found..." instead of \
                  just listing emails.
                - Every single time you use searchWeb, you MUST name at least one actual source from the \
                  results (the title or domain, e.g. "According to Kiplinger..." or "per a Forbes article...") \
                  rather than a vague phrase like "various sources" or "some articles." If the tool didn't \
                  return a usable source name, say plainly that you found general information but no specific \
                  source to cite.
                - When you answer from your own general knowledge, not a tool (opinions, well-known concepts, \
                  explanations), you MUST use a hedge phrase appropriate to your actual confidence: "I believe...", \
                  "It's generally understood that...", "This is a matter of opinion, but...". Never state an \
                  opinion or an unverified fact in the same flat declarative voice you'd use for a verified \
                  tool result.
                - The test: a reader should be able to tell, from phrasing alone and without seeing your tool \
                  calls, exactly which parts of your answer came from a live check versus your own knowledge.

                Response style:
                - Be concise. Default to the shortest response that fully answers the question. Avoid \
                  preamble, filler, restating the question, or padding with unnecessary caveats.
                - Use plain, direct language, like a highly competent executive assistant would.
                - Only add detail, structure, or lists when the content genuinely needs it, not by default.
                - When taking an action (sending an email, creating or deleting a calendar event), briefly \
                  confirm exactly what was done rather than narrating the process.
                """.formatted(today, year, year, year);
    }

    public ChatMemoryService(ChatMessageRepository repository, ConversationRepository conversationRepository, ChatModel chatModel) {
        this.repository = repository;
        this.conversationRepository = conversationRepository;
        this.chatModel = chatModel;
    }

    public ChatMemory getMemory(String conversationId) {
        return memories.computeIfAbsent(conversationId, this::loadMemory);
    }

    private ChatMemory loadMemory(String conversationId) {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        List<ChatMessageEntity> stored = repository.findByConversationIdOrderByTimestampAsc(conversationId);

        if (stored.isEmpty()) {
            String systemPrompt = buildSystemPrompt();
            memory.add(SystemMessage.from(systemPrompt));
            repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.SYSTEM, systemPrompt));
        } else {
            for (ChatMessageEntity entity : stored) {
                switch (entity.getRole()) {
                    case SYSTEM -> memory.add(SystemMessage.from(entity.getContent()));
                    case USER -> memory.add(UserMessage.from(entity.getContent()));
                    case AI -> memory.add(AiMessage.from(entity.getContent()));
                }
            }
        }

        return memory;
    }

    private void touchConversation(String conversationId, String userId, String firstUserMessage) {
        ConversationEntity conversation = conversationRepository.findById(conversationId).orElse(null);

        if (conversation == null) {
            conversation = new ConversationEntity(conversationId, userId, "New chat");
            conversationRepository.save(conversation);
        } else {
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conversation);
        }
    }

    private String generateTitle(String userMessage, String aiResponse) {
        try {
            String prompt = "Summarize the topic of this exchange as a short chat title, 3 to 6 words. " +
                    "No quotes, no punctuation at the end, no prefix like 'Title:'. Just the title text itself.\n\n" +
                    "User: " + truncate(userMessage, 300) + "\nAssistant: " + truncate(aiResponse, 300);

            String title = chatModel.chat(prompt).trim();
            title = title.replaceAll("^[\"']|[\"'.]$", "").trim();

            if (title.isBlank()) {
                return deriveTitleFromMessage(userMessage);
            }
            return title.length() > 60 ? title.substring(0, 60) + "…" : title;
        } catch (Exception e) {
            return deriveTitleFromMessage(userMessage);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private String deriveTitleFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return "New chat";
        }
        String trimmed = message.trim();
        return trimmed.length() > 48 ? trimmed.substring(0, 48) + "…" : trimmed;
    }

    public void addUserMessage(String conversationId, String content) {
        getMemory(conversationId).add(UserMessage.from(content));
        repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.USER, content));
    }

    public void addAiMessage(String conversationId, String content) {
        getMemory(conversationId).add(AiMessage.from(content));
        repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.AI, content));
    }

    public void persistUserMessage(String conversationId, String userId, String content) {
        repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.USER, content));
        touchConversation(conversationId, userId, content);
    }

    public void persistAiMessage(String conversationId, String content) {
        repository.save(new ChatMessageEntity(conversationId, ChatMessageEntity.MessageRole.AI, content));

        ConversationEntity conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation != null && "New chat".equals(conversation.getTitle())) {
            List<ChatMessageEntity> messages = repository.findByConversationIdOrderByTimestampAsc(conversationId);
            String firstUserMessage = messages.stream()
                    .filter(m -> m.getRole() == ChatMessageEntity.MessageRole.USER)
                    .findFirst()
                    .map(ChatMessageEntity::getContent)
                    .orElse("");

            String title = generateTitle(firstUserMessage, content);
            conversation.setTitle(title);
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conversation);
        }
    }
}
