package com.example.aiassistant;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final long MAX_PICTURE_SIZE_BYTES = 2_000_000;

    private final UserProfileRepository userProfileRepository;

    public ProfileController(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    private OidcUser getOidcUser(Principal principal) {
        return (OidcUser) ((OAuth2AuthenticationToken) principal).getPrincipal();
    }

    @GetMapping
    public Map<String, String> getProfile(Principal principal) {
        OidcUser user = getOidcUser(principal);
        UserProfileEntity profile = userProfileRepository.findById(user.getSubject()).orElse(null);

        String displayName = (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().isBlank())
                ? profile.getDisplayName()
                : (user.getFullName() != null ? user.getFullName() : "");

        String nickname = (profile != null && profile.getNickname() != null) ? profile.getNickname() : "";

        String picture = (profile != null && profile.getProfilePicture() != null && !profile.getProfilePicture().isBlank())
                ? profile.getProfilePicture()
                : (user.getPicture() != null ? user.getPicture() : "");

        return Map.of(
                "email", user.getEmail() != null ? user.getEmail() : "",
                "name", displayName,
                "nickname", nickname,
                "picture", picture
        );
    }

    @PutMapping
    public Map<String, String> updateProfile(Principal principal, @RequestBody Map<String, String> body) {
        OidcUser user = getOidcUser(principal);
        UserProfileEntity profile = userProfileRepository.findById(user.getSubject())
                .orElse(new UserProfileEntity(user.getSubject()));

        if (body.containsKey("name")) {
            profile.setDisplayName(body.get("name"));
        }
        if (body.containsKey("nickname")) {
            profile.setNickname(body.get("nickname"));
        }
        profile.setUpdatedAt(LocalDateTime.now());
        userProfileRepository.save(profile);

        return getProfile(principal);
    }

    @PutMapping("/picture")
    public Map<String, String> updatePicture(Principal principal, @RequestBody Map<String, String> body) {
        String base64Image = body.get("image");
        if (base64Image == null || base64Image.isBlank()) {
            throw new IllegalArgumentException("No image provided");
        }
        if (base64Image.length() > MAX_PICTURE_SIZE_BYTES) {
            throw new IllegalArgumentException("Image too large; please use a picture under ~1.5MB");
        }

        OidcUser user = getOidcUser(principal);
        UserProfileEntity profile = userProfileRepository.findById(user.getSubject())
                .orElse(new UserProfileEntity(user.getSubject()));

        profile.setProfilePicture(base64Image);
        profile.setUpdatedAt(LocalDateTime.now());
        userProfileRepository.save(profile);

        return getProfile(principal);
    }

    @DeleteMapping("/picture")
    public Map<String, String> removePicture(Principal principal) {
        OidcUser user = getOidcUser(principal);
        UserProfileEntity profile = userProfileRepository.findById(user.getSubject()).orElse(null);

        if (profile != null) {
            profile.setProfilePicture(null);
            profile.setUpdatedAt(LocalDateTime.now());
            userProfileRepository.save(profile);
        }

        return getProfile(principal);
    }
}
