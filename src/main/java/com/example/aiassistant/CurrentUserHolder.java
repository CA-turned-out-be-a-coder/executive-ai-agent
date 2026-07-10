package com.example.aiassistant;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserHolder {

    private volatile OidcUser currentUser;

    public void setCurrentUser(OidcUser user) {
        this.currentUser = user;
    }

    public OidcUser getCurrentUser() {
        if (currentUser == null) {
            throw new IllegalStateException("No authenticated Google user found");
        }
        return currentUser;
    }
}
