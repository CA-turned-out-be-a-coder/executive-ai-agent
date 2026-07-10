package com.example.aiassistant;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {

    @GetMapping("/api/me")
    public String me(java.security.Principal principal) {
        OidcUser user = (OidcUser) ((org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken) principal).getPrincipal();
        return user.getEmail();
    }
}