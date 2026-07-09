package com.example.aiassistant;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssistantTools {

    private final CalendarService calendarService;
    private final GmailService gmailService;

    public AssistantTools(CalendarService calendarService, GmailService gmailService) {
        this.calendarService = calendarService;
        this.gmailService = gmailService;
    }

    private OidcUser getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser;
        }
        throw new IllegalStateException("No authenticated Google user found");
    }

    @Tool("Get the user's upcoming calendar events")
    public String getUpcomingEvents() {
        try {
            List<String> events = calendarService.getUpcomingEvents(getCurrentUser());
            if (events.isEmpty()) return "No upcoming events found.";
            return String.join("\n", events);
        } catch (Exception e) {
            return "Could not retrieve calendar events: " + e.getMessage();
        }
    }

    @Tool("Get the user's recent email subjects and senders")
    public String getRecentEmails() {
        try {
            List<String> emails = gmailService.getRecentSubjects(getCurrentUser());
            if (emails.isEmpty()) return "No recent emails found.";
            return String.join("\n", emails);
        } catch (Exception e) {
            return "Could not retrieve emails: " + e.getMessage();
        }
    }
}