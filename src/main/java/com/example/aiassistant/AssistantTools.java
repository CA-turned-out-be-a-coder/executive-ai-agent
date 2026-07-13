package com.example.aiassistant;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssistantTools {

    private final CalendarService calendarService;
    private final GmailService gmailService;
    private final CurrentUserHolder currentUserHolder;
    private final WeatherService weatherService;
    private final StockService stockService;
    private final WebSearchService webSearchService;

    public AssistantTools(CalendarService calendarService, GmailService gmailService, CurrentUserHolder currentUserHolder,
                           WeatherService weatherService, StockService stockService, WebSearchService webSearchService) {
        this.calendarService = calendarService;
        this.gmailService = gmailService;
        this.currentUserHolder = currentUserHolder;
        this.weatherService = weatherService;
        this.stockService = stockService;
        this.webSearchService = webSearchService;
    }

    private OidcUser getCurrentUser() {
        return currentUserHolder.getCurrentUser();
    }

    @Tool("Get the user's upcoming calendar events. Each result includes an event ID in brackets, " +
            "e.g. [ID: abc123], which must be used exactly as shown if the user later asks to update or delete that event.")
    public String getUpcomingEvents() {
        try {
            List<String> events = calendarService.getUpcomingEvents(getCurrentUser());
            if (events.isEmpty()) return "No upcoming events found.";
            return String.join("\n", events);
        } catch (Exception e) {
            e.printStackTrace();
            return "Could not retrieve calendar events: " + e.getMessage();
        }
    }

    @Tool("Get the user's recent email subjects and senders. Each result includes a message ID in brackets, " +
            "e.g. [ID: abc123], which must be used with readEmail to see the full body content of a specific email.")
    public String getRecentEmails() {
        try {
            List<String> emails = gmailService.getRecentSubjects(getCurrentUser());
            if (emails.isEmpty()) return "No recent emails found.";
            return String.join("\n", emails);
        } catch (Exception e) {
            return "Could not retrieve emails: " + e.getMessage();
        }
    }

    @Tool("Search the user's Gmail using Gmail's native search syntax, e.g. 'from:sarah@company.com is:unread', " +
            "'subject:invoice', 'has:attachment newer_than:7d'. Use this whenever the user wants to find specific " +
            "emails rather than just see the most recent ones. Each result includes a message ID in brackets, " +
            "e.g. [ID: abc123], which must be used with readEmail to see the full body content of a specific email.")
    public String searchEmails(String query) {
        try {
            List<String> emails = gmailService.searchEmails(getCurrentUser(), query);
            if (emails.isEmpty()) return "No emails found matching that search.";
            return String.join("\n", emails);
        } catch (Exception e) {
            return "Could not search emails: " + e.getMessage();
        }
    }

    @Tool("Reads the full body content of a specific email, not just the subject/sender. Requires the exact " +
            "messageId shown in brackets from getRecentEmails or searchEmails, e.g. 'abc123'. Use this whenever " +
            "the user asks what an email says, what it's about, or wants you to summarize or act on its actual content.")
    public String readEmail(String messageId) {
        try {
            return gmailService.getEmailContent(getCurrentUser(), messageId);
        } catch (Exception e) {
            return "Could not read email content: " + e.getMessage();
        }
    }

    @Tool("Creates a draft email in the user's Gmail. Does not send it. " +
            "Use this by default when the user asks to compose or write an email.")
    public String createEmailDraft(String toEmail, String subject, String bodyText) {
        try {
            gmailService.createDraft(getCurrentUser(), toEmail, subject, bodyText);
            return "Draft created successfully to " + toEmail + " with subject '" + subject + "'.";
        } catch (Exception e) {
            return "Failed to create draft: " + e.getMessage();
        }
    }

    @Tool("Sends an email immediately via the user's Gmail. Only use this when the user has " +
            "explicitly confirmed they want the email sent, not just drafted.")
    public String sendEmail(String toEmail, String subject, String bodyText) {
        try {
            gmailService.sendEmail(getCurrentUser(), toEmail, subject, bodyText);
            return "Email sent successfully to " + toEmail + ".";
        } catch (Exception e) {
            return "Failed to send email: " + e.getMessage();
        }
    }

    @Tool("Creates a new event on the user's primary Google Calendar. " +
            "startIso and endIso must be full ISO-8601 datetimes with timezone offset, e.g. 2026-07-10T14:00:00-07:00.")
    public String createCalendarEvent(String summary, String description, String startIso, String endIso) {
        try {
            String link = calendarService.createEvent(getCurrentUser(), summary, description, startIso, endIso);
            return "Event '" + summary + "' created: " + link;
        } catch (Exception e) {
            return "Failed to create event: " + e.getMessage();
        }
    }

    @Tool("Updates an existing calendar event. Requires the exact eventId shown in brackets from getUpcomingEvents, " +
            "e.g. 'abc123'. Only pass the fields that should change; leave others as empty strings to keep them unchanged. " +
            "startIso and endIso must be full ISO-8601 datetimes with timezone offset if provided.")
    public String updateCalendarEvent(String eventId, String summary, String description, String startIso, String endIso) {
        try {
            String link = calendarService.updateEvent(getCurrentUser(), eventId, summary, description, startIso, endIso);
            return "Event updated: " + link;
        } catch (Exception e) {
            return "Failed to update event: " + e.getMessage();
        }
    }

    @Tool("Deletes a calendar event permanently. Requires the exact eventId shown in brackets from getUpcomingEvents, " +
            "e.g. 'abc123'. Only use this after the user has explicitly confirmed they want the event deleted or cancelled.")
    public String deleteCalendarEvent(String eventId) {
        try {
            calendarService.deleteEvent(getCurrentUser(), eventId);
            return "Event deleted successfully.";
        } catch (Exception e) {
            return "Failed to delete event: " + e.getMessage();
        }
    }

    @Tool("Get the current weather for a city or location, e.g. 'Phoenix', 'London, UK', 'Tokyo'. " +
            "Returns temperature in Fahrenheit, conditions, humidity, and wind speed.")
    public String getWeather(String location) {
        try {
            return weatherService.getCurrentWeather(location);
        } catch (Exception e) {
            return "Could not retrieve weather: " + e.getMessage();
        }
    }

    @Tool("Get the current stock price and daily change for a ticker symbol, e.g. 'AAPL', 'TSLA', 'GOOGL'.")
    public String getStockQuote(String symbol) {
        try {
            return stockService.getQuote(symbol);
        } catch (Exception e) {
            return "Could not retrieve stock quote: " + e.getMessage();
        }
    }

    @Tool("Search the web for quick facts about well-known topics, people, places, or things. " +
            "This only returns short factual summaries (similar to an encyclopedia entry), not current news, " +
            "recent events, or detailed multi-result search listings. Do not rely on this for anything time-sensitive " +
            "or that requires up-to-the-minute information.")
    public String searchWeb(String query) {
        try {
            return webSearchService.search(query);
        } catch (Exception e) {
            return "Could not perform web search: " + e.getMessage();
        }
    }
}
