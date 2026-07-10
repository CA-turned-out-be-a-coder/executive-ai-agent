package com.example.aiassistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "openai.api.key=test-key-for-context-load-only",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class AiAssistantApplicationTests {

    @Test
    void contextLoads() {
        // If the Spring context fails to start, this test fails automatically.
        // This catches misconfigured beans, missing dependencies, and wiring mistakes early.
    }

}
