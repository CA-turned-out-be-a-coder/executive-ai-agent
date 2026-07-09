package com.example.aiassistant;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GmailService {

    private final OAuth2AuthorizedClientService authorizedClientService;

    public GmailService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    public List<String> getRecentSubjects(OidcUser principal) throws Exception {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                "google", principal.getName());

        String accessTokenValue = client.getAccessToken().getTokenValue();

        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessTokenValue, null));

        Gmail gmailClient = new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Executive AI Agent")
                .build();

        ListMessagesResponse response = gmailClient.users().messages()
                .list("me")
                .setMaxResults(10L)
                .execute();

        List<String> subjects = new ArrayList<>();
        if (response.getMessages() != null) {
            for (Message m : response.getMessages()) {
                Message full = gmailClient.users().messages()
                        .get("me", m.getId())
                        .setFormat("metadata")
                        .setMetadataHeaders(List.of("Subject", "From"))
                        .execute();

                String subject = "(no subject)";
                String from = "(unknown sender)";
                if (full.getPayload() != null && full.getPayload().getHeaders() != null) {
                    for (var header : full.getPayload().getHeaders()) {
                        if (header.getName().equalsIgnoreCase("Subject")) {
                            subject = header.getValue();
                        }
                        if (header.getName().equalsIgnoreCase("From")) {
                            from = header.getValue();
                        }
                    }
                }
                subjects.add(subject + " — from " + from);
            }
        }
        return subjects;
    }
}