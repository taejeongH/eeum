package org.ssafy.eeum.global.infra.gms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmsService {

    @Value("${spring.gms.url}")
    private String apiUrl;

    @Value("${spring.gms.key}")
    private String apiKey;

    @Value("${spring.gms.model}")
    private String model;

    public boolean analyzeSentiment(String text) {
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(apiUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "developer", "content",
                                    "You are an emergency assistant. Analyze the user's text. Return ONLY 'EMERGENCY' for dangerous situations (pain, help needed, negative) or 'SAFE' for safe situations (okay, no problem)."),
                            Map.of("role", "user", "content", text)));

            Map response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    log.info("GMS Analysis Result: {}", content);
                    return content != null && content.toUpperCase().contains("EMERGENCY");
                }
            }
        } catch (Exception e) {
            log.error("GMS Analysis Error: {}", e.getMessage());
        }
        return false;
    }
}
