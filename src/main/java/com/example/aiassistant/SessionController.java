package com.example.aiassistant;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
public class SessionController {

    @GetMapping("/api/me")
    public Map<String, String> me(Principal principal) {
        OidcUser user = (OidcUser) ((OAuth2AuthenticationToken) principal).getPrincipal();

        return Map.of(
                "email", user.getEmail() != null ? user.getEmail() : "",
                "name", user.getFullName() != null ? user.getFullName() : "",
                "picture", user.getPicture() != null ? user.getPicture() : ""
        );
    }
}
