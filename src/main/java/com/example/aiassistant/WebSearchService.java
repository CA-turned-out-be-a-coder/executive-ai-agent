package com.example.aiassistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class WebSearchService {

    private final RestClient restClient = RestClient.create();

    @Value("${tavily.api.key}")
    private String apiKey;

    public String search(String query) {
        Map<String, Object> requestBody = Map.of(
                "query", query,
                "search_depth", "basic",
                "include_answer", true,
                "max_results", 5
        );

        Map<String, Object> response = restClient.post()
                .uri("https://api.tavily.com/search")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        StringBuilder result = new StringBuilder();

        String answer = (String) response.get("answer");
        if (answer != null && !answer.isBlank()) {
            result.append(answer).append("\n\n");
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results != null && !results.isEmpty()) {
            result.append("Sources:\n");
            for (Map<String, Object> item : results) {
                String title = (String) item.get("title");
                String url = (String) item.get("url");
                String content = (String) item.get("content");

                result.append("- ").append(title).append(" (").append(url).append(")");
                if (content != null && !content.isBlank()) {
                    String snippet = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                    result.append(": ").append(snippet);
                }
                result.append("\n");
            }
        }

        if (result.isEmpty()) {
            return "No results found for '" + query + "'.";
        }

        return result.toString();
    }
}
