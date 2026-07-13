package com.example.aiassistant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    List<ConversationEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

    boolean existsByIdAndUserId(String id, String userId);
}
