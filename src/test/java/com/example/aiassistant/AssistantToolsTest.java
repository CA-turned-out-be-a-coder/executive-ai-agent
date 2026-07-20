package com.example.aiassistant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantToolsTest {

    @Mock
    private CalendarService calendarService;

    @Mock
    private GmailService gmailService;

    @Mock
    private CurrentUserHolder currentUserHolder;

    @Mock
    private WeatherService weatherService;

    @Mock
    private StockService stockService;

    @Mock
    private WebSearchService webSearchService;

    @Mock
    private OidcUser oidcUser;

    private AssistantTools assistantTools;

    @BeforeEach
    void setUp() {
        assistantTools = new AssistantTools(calendarService, gmailService, currentUserHolder,
                weatherService, stockService, webSearchService);
    }

    @Test
    void getUpcomingEvents_returnsJoinedEventList_whenEventsExist() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(calendarService.getUpcomingEvents(oidcUser)).thenReturn(List.of("Meeting at 10am", "Flight at 2pm"));

        String result = assistantTools.getUpcomingEvents();

        assertEquals("Your calendar shows:\nMeeting at 10am\nFlight at 2pm", result);
    }

    @Test
    void getUpcomingEvents_returnsNoEventsMessage_whenListIsEmpty() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(calendarService.getUpcomingEvents(oidcUser)).thenReturn(Collections.emptyList());

        String result = assistantTools.getUpcomingEvents();

        assertEquals("Your calendar shows no upcoming events.", result);
    }

    @Test
    void getUpcomingEvents_returnsErrorMessage_whenServiceThrowsException() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(calendarService.getUpcomingEvents(oidcUser)).thenThrow(new RuntimeException("Calendar API unavailable"));

        String result = assistantTools.getUpcomingEvents();

        assertTrue(result.startsWith("Could not retrieve calendar events:"));
        assertTrue(result.contains("Calendar API unavailable"));
    }

    @Test
    void createEmailDraft_returnsSuccessMessage_whenDraftCreatedSuccessfully() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);

        String result = assistantTools.createEmailDraft("test@example.com", "Test Subject", "Test body");

        assertEquals("Draft created successfully to test@example.com with subject 'Test Subject'.", result);
    }

    @Test
    void createEmailDraft_returnsErrorMessage_whenGmailServiceThrowsException() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        org.mockito.Mockito.doThrow(new RuntimeException("Gmail API error"))
                .when(gmailService).createDraft(any(), anyString(), anyString(), anyString());

        String result = assistantTools.createEmailDraft("test@example.com", "Test Subject", "Test body");

        assertTrue(result.startsWith("Failed to create draft:"));
        assertTrue(result.contains("Gmail API error"));
    }

    @Test
    void sendEmail_returnsSuccessMessage_whenSentSuccessfully() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);

        String result = assistantTools.sendEmail("test@example.com", "Test Subject", "Test body");

        assertEquals("Email sent successfully to test@example.com.", result);
    }

    @Test
    void createCalendarEvent_returnsSuccessMessageWithLink_whenCreatedSuccessfully() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(calendarService.createEvent(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("https://calendar.google.com/event/abc123");

        String result = assistantTools.createCalendarEvent(
                "Team Meeting", "Weekly sync", "2026-07-10T10:00:00-07:00", "2026-07-10T11:00:00-07:00");

        assertEquals("Event 'Team Meeting' created: https://calendar.google.com/event/abc123", result);
    }

    @Test
    void searchEmails_returnsJoinedResults_whenMatchesFound() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(gmailService.searchEmails(oidcUser, "from:sarah@company.com"))
                .thenReturn(List.of("Invoice — from sarah@company.com", "Follow up — from sarah@company.com"));

        String result = assistantTools.searchEmails("from:sarah@company.com");

        assertEquals("Searching your inbox, found:\nInvoice — from sarah@company.com\nFollow up — from sarah@company.com", result);
    }

    @Test
    void searchEmails_returnsNoResultsMessage_whenListIsEmpty() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(gmailService.searchEmails(oidcUser, "subject:nonexistent")).thenReturn(Collections.emptyList());

        String result = assistantTools.searchEmails("subject:nonexistent");

        assertEquals("Searching your inbox, no emails matched that search.", result);
    }

    @Test
    void searchEmails_returnsErrorMessage_whenServiceThrowsException() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(gmailService.searchEmails(oidcUser, "from:x")).thenThrow(new RuntimeException("Gmail search failed"));

        String result = assistantTools.searchEmails("from:x");

        assertTrue(result.startsWith("Could not search emails:"));
        assertTrue(result.contains("Gmail search failed"));
    }

    @Test
    void updateCalendarEvent_returnsSuccessMessageWithLink_whenUpdatedSuccessfully() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(calendarService.updateEvent(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("https://calendar.google.com/event/abc123");

        String result = assistantTools.updateCalendarEvent(
                "abc123", "Updated Meeting", "", "2026-07-10T15:00:00-07:00", "");

        assertEquals("Event updated: https://calendar.google.com/event/abc123", result);
    }

    @Test
    void updateCalendarEvent_returnsErrorMessage_whenServiceThrowsException() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(calendarService.updateEvent(any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Event not found"));

        String result = assistantTools.updateCalendarEvent("bad-id", "Title", "", "", "");

        assertTrue(result.startsWith("Failed to update event:"));
        assertTrue(result.contains("Event not found"));
    }

    @Test
    void deleteCalendarEvent_returnsSuccessMessage_whenDeletedSuccessfully() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);

        String result = assistantTools.deleteCalendarEvent("abc123");

        assertEquals("Event deleted successfully.", result);
    }

    @Test
    void deleteCalendarEvent_returnsErrorMessage_whenServiceThrowsException() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        org.mockito.Mockito.doThrow(new RuntimeException("Event not found"))
                .when(calendarService).deleteEvent(any(), anyString());

        String result = assistantTools.deleteCalendarEvent("bad-id");

        assertTrue(result.startsWith("Failed to delete event:"));
        assertTrue(result.contains("Event not found"));
    }

    @Test
    void readEmail_returnsContent_whenSuccessful() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(gmailService.getEmailContent(oidcUser, "msg123"))
                .thenReturn("Subject: Just a Note\nFrom: friend@example.com\n\nHey, how are you?");

        String result = assistantTools.readEmail("msg123");

        assertEquals("Reading that email directly from your inbox:\nSubject: Just a Note\nFrom: friend@example.com\n\nHey, how are you?", result);
    }

    @Test
    void readEmail_returnsErrorMessage_whenServiceThrowsException() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(gmailService.getEmailContent(oidcUser, "bad-id")).thenThrow(new RuntimeException("Message not found"));

        String result = assistantTools.readEmail("bad-id");

        assertTrue(result.startsWith("Could not read email content:"));
        assertTrue(result.contains("Message not found"));
    }

    @Test
    void getWeather_returnsWeatherString_whenSuccessful() throws Exception {
        when(weatherService.getCurrentWeather("Phoenix")).thenReturn("Weather in Phoenix, US: 105°F, clear sky");

        String result = assistantTools.getWeather("Phoenix");

        assertEquals("Weather in Phoenix, US: 105°F, clear sky", result);
    }

    @Test
    void getWeather_returnsErrorMessage_whenServiceThrowsException() {
        when(weatherService.getCurrentWeather("Nowhere")).thenThrow(new RuntimeException("Could not find location: Nowhere"));

        String result = assistantTools.getWeather("Nowhere");

        assertTrue(result.startsWith("Could not retrieve weather:"));
        assertTrue(result.contains("Could not find location: Nowhere"));
    }

    @Test
    void getStockQuote_returnsQuoteString_whenSuccessful() throws Exception {
        when(stockService.getQuote("AAPL")).thenReturn("Apple Inc. (AAPL): 213.55 USD, up 2.10 (0.99%) today");

        String result = assistantTools.getStockQuote("AAPL");

        assertEquals("Apple Inc. (AAPL): 213.55 USD, up 2.10 (0.99%) today", result);
    }

    @Test
    void getStockQuote_returnsErrorMessage_whenServiceThrowsException() {
        when(stockService.getQuote("BADSYM")).thenThrow(new RuntimeException("Could not find stock symbol 'BADSYM'"));

        String result = assistantTools.getStockQuote("BADSYM");

        assertTrue(result.startsWith("Could not retrieve stock quote:"));
        assertTrue(result.contains("Could not find stock symbol 'BADSYM'"));
    }

    @Test
    void searchWeb_returnsResultString_whenSuccessful() {
        when(webSearchService.search("Super Bowl 2026 winner", true))
                .thenReturn("The Kansas City Chiefs won Super Bowl LX.\n\nSources:\n- ...");

        String result = assistantTools.searchWeb("Super Bowl 2026 winner", true);

        assertTrue(result.startsWith("The Kansas City Chiefs won Super Bowl LX."));
    }

    @Test
    void searchWeb_returnsErrorMessage_whenServiceThrowsException() {
        when(webSearchService.search(anyString(), anyBoolean())).thenThrow(new RuntimeException("Tavily API unavailable"));

        String result = assistantTools.searchWeb("some query", false);

        assertTrue(result.startsWith("Could not perform web search:"));
        assertTrue(result.contains("Tavily API unavailable"));
    }
}
