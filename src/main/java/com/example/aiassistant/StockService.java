package com.example.aiassistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class StockService {

    private final RestClient restClient = RestClient.create();

    @Value("${twelvedata.api.key}")
    private String apiKey;

    public String getQuote(String symbol) {
        Map<String, Object> response = restClient.get()
                .uri("https://api.twelvedata.com/quote?symbol={symbol}&apikey={apiKey}", symbol.toUpperCase(), apiKey)
                .retrieve()
                .body(Map.class);

        if (response.containsKey("status") && "error".equals(response.get("status"))) {
            String message = (String) response.getOrDefault("message", "Unknown error");
            throw new IllegalArgumentException("Could not find stock symbol '" + symbol + "': " + message);
        }

        String name = (String) response.getOrDefault("name", symbol.toUpperCase());
        String currency = (String) response.getOrDefault("currency", "USD");
        double price = Double.parseDouble((String) response.get("close"));
        double change = Double.parseDouble((String) response.get("change"));
        double percentChange = Double.parseDouble((String) response.get("percent_change"));

        String direction = change >= 0 ? "up" : "down";

        return String.format("%s (%s): %.2f %s, %s %.2f (%.2f%%) today",
                name, symbol.toUpperCase(), price, currency, direction, Math.abs(change), Math.abs(percentChange));
    }
}
