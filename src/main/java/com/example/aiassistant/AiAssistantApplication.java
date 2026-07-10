package com.example.aiassistant;

import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiAssistantApplication {

    public static void main(String[] args) {
        String dbUrl = System.getenv().getOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:5432/aiassistant");
        String dbUsername = System.getenv().getOrDefault("DATABASE_USERNAME", "aiassistant_user");
        String dbPassword = System.getenv().getOrDefault("DATABASE_PASSWORD", "localdevpassword");

        Flyway.configure()
                .dataSource(dbUrl, dbUsername, dbPassword)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        SpringApplication.run(AiAssistantApplication.class, args);
    }

}
