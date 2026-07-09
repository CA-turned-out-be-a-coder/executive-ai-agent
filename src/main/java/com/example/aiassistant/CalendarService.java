package com.example.aiassistant;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
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

    public List<String> getUpcomingEvents(OidcUser principal) throws Exception {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                "google", principal.getName());

        String accessTokenValue = client.getAccessToken().getTokenValue();

        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessTokenValue, null));

        Calendar calendarClient = new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Executive AI Agent")
                .build();

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
            summaries.add(event.getSummary() + " (" + start + ")");
        }
        return summaries;
    }
}