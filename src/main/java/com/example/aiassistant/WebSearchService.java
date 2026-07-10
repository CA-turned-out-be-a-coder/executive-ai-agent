package com.example.aiassistant;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class WebSearchService {

    private final RestClient restClient = RestClient.create();

    public String search(String query) {
        Map<String, Object> response = restClient.get()
                .uri("https://api.duckduckgo.com/?q={query}&format=json&no_html=1&skip_disambig=1", query)
                .retrieve()
                .body(Map.class);

        String abstractText = (String) response.get("AbstractText");
        String abstractSource = (String) response.get("AbstractSource");
        String abstractUrl = (String) response.get("AbstractURL");

        if (abstractText != null && !abstractText.isBlank()) {
            String result = abstractText;
            if (abstractSource != null && !abstractSource.isBlank()) {
                result += " (Source: " + abstractSource;
                if (abstractUrl != null && !abstractUrl.isBlank()) {
                    result += ", " + abstractUrl;
                }
                result += ")";
            }
            return result;
        }

        List<Map<String, Object>> relatedTopics = (List<Map<String, Object>>) response.get("RelatedTopics");
        if (relatedTopics != null && !relatedTopics.isEmpty()) {
            Map<String, Object> first = relatedTopics.get(0);
            String text = (String) first.get("Text");
            if (text != null && !text.isBlank()) {
                return text;
            }
        }

        return "No quick answer found for '" + query + "'. This search tool only covers well-known facts and topics, " +
                "not current news or detailed web results.";
    }
}
