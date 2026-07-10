package com.example.aiassistant;

import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiAssistantApplication {

    public static void main(String[] args) {
        Flyway.configure()
                .dataSource("jdbc:postgresql://localhost:5432/aiassistant", "aiassistant_user", "localdevpassword")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        SpringApplication.run(AiAssistantApplication.class, args);
    }

}
