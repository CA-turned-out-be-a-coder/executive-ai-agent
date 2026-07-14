CREATE TABLE user_profiles (
    user_id VARCHAR(255) PRIMARY KEY,
    display_name VARCHAR(255),
    nickname VARCHAR(255),
    profile_picture TEXT,
    updated_at TIMESTAMP NOT NULL
);
