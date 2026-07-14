package com.example.aiassistant;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

    @Id
    private String userId;

    private String displayName;

    private String nickname;

    @Column(columnDefinition = "TEXT")
    private String profilePicture;

    private LocalDateTime updatedAt;

    public UserProfileEntity() {
    }

    public UserProfileEntity(String userId) {
        this.userId = userId;
        this.updatedAt = LocalDateTime.now();
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
