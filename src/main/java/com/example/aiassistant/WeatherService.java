package com.example.aiassistant;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class WeatherService {

    private final RestClient restClient = RestClient.create();

    public String getCurrentWeather(String location) {
        Map<String, Object> geoResponse = restClient.get()
                .uri("https://geocoding-api.open-meteo.com/v1/search?name={location}&count=1", location)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> results = (List<Map<String, Object>>) geoResponse.get("results");
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("Could not find location: " + location);
        }

        Map<String, Object> place = results.get(0);
        double lat = ((Number) place.get("latitude")).doubleValue();
        double lon = ((Number) place.get("longitude")).doubleValue();
        String resolvedName = (String) place.get("name");
        String country = (String) place.get("country");

        Map<String, Object> weatherResponse = restClient.get()
                .uri("https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&temperature_unit=fahrenheit",
                        lat, lon)
                .retrieve()
                .body(Map.class);

        Map<String, Object> current = (Map<String, Object>) weatherResponse.get("current");
        double tempF = ((Number) current.get("temperature_2m")).doubleValue();
        double humidity = ((Number) current.get("relative_humidity_2m")).doubleValue();
        double windSpeed = ((Number) current.get("wind_speed_10m")).doubleValue();
        int weatherCode = ((Number) current.get("weather_code")).intValue();

        String condition = describeWeatherCode(weatherCode);

        return String.format("Per today's live weather check: %s, %s is currently %.0f°F, %s, %.0f%% humidity, wind %.0f mph",
                resolvedName, country, tempF, condition, humidity, windSpeed);
    }

    private String describeWeatherCode(int code) {
        if (code == 0) return "clear sky";
        if (code <= 3) return "partly cloudy";
        if (code <= 48) return "foggy";
        if (code <= 57) return "drizzle";
        if (code <= 67) return "rain";
        if (code <= 77) return "snow";
        if (code <= 82) return "rain showers";
        if (code <= 86) return "snow showers";
        if (code <= 99) return "thunderstorm";
        return "unknown conditions";
    }
}
