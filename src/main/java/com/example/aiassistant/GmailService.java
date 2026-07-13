package com.example.aiassistant;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
public class GmailService {

    private final OAuth2AuthorizedClientService authorizedClientService;

    public GmailService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    private Gmail buildClient(OidcUser principal) throws Exception {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                "google", principal.getName());

        String accessTokenValue = client.getAccessToken().getTokenValue();

        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessTokenValue, null));

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Executive AI Agent")
                .build();
    }

    private List<String> formatMessages(Gmail gmailClient, List<Message> messages) throws Exception {
        List<String> results = new ArrayList<>();
        if (messages == null) {
            return results;
        }
        for (Message m : messages) {
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
            results.add(subject + " — from " + from + " [ID: " + m.getId() + "]");
        }
        return results;
    }

    public List<String> getRecentSubjects(OidcUser principal) throws Exception {
        Gmail gmailClient = buildClient(principal);

        ListMessagesResponse response = gmailClient.users().messages()
                .list("me")
                .setMaxResults(10L)
                .execute();

        return formatMessages(gmailClient, response.getMessages());
    }

    public List<String> searchEmails(OidcUser principal, String query) throws Exception {
        Gmail gmailClient = buildClient(principal);

        ListMessagesResponse response = gmailClient.users().messages()
                .list("me")
                .setQ(query)
                .setMaxResults(10L)
                .execute();

        return formatMessages(gmailClient, response.getMessages());
    }

    public String getEmailContent(OidcUser principal, String messageId) throws Exception {
        Gmail gmailClient = buildClient(principal);

        Message full = gmailClient.users().messages()
                .get("me", messageId)
                .setFormat("full")
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

        String body = extractBody(full.getPayload());
        if (body == null || body.isBlank()) {
            body = "(no readable body content found)";
        }

        return "Subject: " + subject + "\nFrom: " + from + "\n\n" + body;
    }

    private String extractBody(MessagePart part) {
        if (part == null) {
            return null;
        }

        if (part.getBody() != null && part.getBody().getData() != null
                && ("text/plain".equalsIgnoreCase(part.getMimeType())
                    || "text/html".equalsIgnoreCase(part.getMimeType()))) {
            byte[] decoded = Base64.decodeBase64(part.getBody().getData());
            return new String(decoded, StandardCharsets.UTF_8);
        }

        if (part.getParts() != null) {
            String plainFallback = null;
            for (MessagePart child : part.getParts()) {
                String childResult = extractBody(child);
                if (childResult != null) {
                    if ("text/plain".equalsIgnoreCase(child.getMimeType())) {
                        return childResult;
                    }
                    if (plainFallback == null) {
                        plainFallback = childResult;
                    }
                }
            }
            return plainFallback;
        }

        return null;
    }

    private Message buildMimeMessage(String toEmail, String subject, String bodyText) throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage email = new MimeMessage(session);
        try {
            email.setFrom(new InternetAddress("me"));
            email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(toEmail));
            email.setSubject(subject);
            email.setText(bodyText);
        } catch (Exception e) {
            throw new MessagingException("Failed to build email", e);
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            email.writeTo(buffer);
            String encodedEmail = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());
            Message message = new Message();
            message.setRaw(encodedEmail);
            return message;
        } catch (Exception e) {
            throw new MessagingException("Failed to encode email", e);
        }
    }

    public Draft createDraft(OidcUser principal, String toEmail, String subject, String bodyText) throws Exception {
        Gmail gmailClient = buildClient(principal);
        Message message = buildMimeMessage(toEmail, subject, bodyText);
        Draft draft = new Draft();
        draft.setMessage(message);
        return gmailClient.users().drafts().create("me", draft).execute();
    }

    public Message sendEmail(OidcUser principal, String toEmail, String subject, String bodyText) throws Exception {
        Gmail gmailClient = buildClient(principal);
        Message message = buildMimeMessage(toEmail, subject, bodyText);
        return gmailClient.users().messages().send("me", message).execute();
    }
}
