package com.example.aiassistant;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class CalendarService {

    private final OAuth2AuthorizedClientService authorizedClientService;

    public CalendarService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    private Calendar buildClient(OidcUser principal) throws Exception {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                "google", principal.getName());

        String accessTokenValue = client.getAccessToken().getTokenValue();

        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessTokenValue, null));

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Executive AI Agent")
                .build();
    }

    public List<String> getUpcomingEvents(OidcUser principal) throws Exception {
        Calendar calendarClient = buildClient(principal);

        Events events = calendarClient.events().list("primary")
                .setMaxResults(10)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .setTimeMin(new DateTime(new Date()))
                .execute();

        List<String> summaries = new ArrayList<>();
        for (Event event : events.getItems()) {
            String start = event.getStart().getDateTime() != null
                    ? event.getStart().getDateTime().toString()
                    : event.getStart().getDate().toString();
            summaries.add(event.getSummary() + " (" + start + ") [ID: " + event.getId() + "]");
        }
        return summaries;
    }

    public String createEvent(OidcUser principal, String summary, String description,
                              String startIso, String endIso) throws Exception {
        Calendar calendarClient = buildClient(principal);

        Event event = new Event()
                .setSummary(summary)
                .setDescription(description);

        event.setStart(new EventDateTime().setDateTime(new DateTime(startIso)));
        event.setEnd(new EventDateTime().setDateTime(new DateTime(endIso)));

        Event created = calendarClient.events().insert("primary", event).execute();
        return created.getHtmlLink();
    }

    public String updateEvent(OidcUser principal, String eventId, String summary, String description,
                               String startIso, String endIso) throws Exception {
        Calendar calendarClient = buildClient(principal);

        Event event = calendarClient.events().get("primary", eventId).execute();

        if (summary != null && !summary.isBlank()) {
            event.setSummary(summary);
        }
        if (description != null && !description.isBlank()) {
            event.setDescription(description);
        }
        if (startIso != null && !startIso.isBlank()) {
            event.setStart(new EventDateTime().setDateTime(new DateTime(startIso)));
        }
        if (endIso != null && !endIso.isBlank()) {
            event.setEnd(new EventDateTime().setDateTime(new DateTime(endIso)));
        }

        Event updated = calendarClient.events().update("primary", eventId, event).execute();
        return updated.getHtmlLink();
    }

    public void deleteEvent(OidcUser principal, String eventId) throws Exception {
        Calendar calendarClient = buildClient(principal);
        calendarClient.events().delete("primary", eventId).execute();
    }
}
