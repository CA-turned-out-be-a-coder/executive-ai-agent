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
    private OidcUser oidcUser;

    private AssistantTools assistantTools;

    @BeforeEach
    void setUp() {
        assistantTools = new AssistantTools(calendarService, gmailService, currentUserHolder);
    }

    @Test
    void getUpcomingEvents_returnsJoinedEventList_whenEventsExist() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(calendarService.getUpcomingEvents(oidcUser)).thenReturn(List.of("Meeting at 10am", "Flight at 2pm"));

        String result = assistantTools.getUpcomingEvents();

        assertEquals("Meeting at 10am\nFlight at 2pm", result);
    }

    @Test
    void getUpcomingEvents_returnsNoEventsMessage_whenListIsEmpty() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(calendarService.getUpcomingEvents(oidcUser)).thenReturn(Collections.emptyList());

        String result = assistantTools.getUpcomingEvents();

        assertEquals("No upcoming events found.", result);
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

        assertEquals("Invoice — from sarah@company.com\nFollow up — from sarah@company.com", result);
    }

    @Test
    void searchEmails_returnsNoResultsMessage_whenListIsEmpty() throws Exception {
        when(currentUserHolder.getCurrentUser()).thenReturn(oidcUser);
        when(gmailService.searchEmails(oidcUser, "subject:nonexistent")).thenReturn(Collections.emptyList());

        String result = assistantTools.searchEmails("subject:nonexistent");

        assertEquals("No emails found matching that search.", result);
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
}
