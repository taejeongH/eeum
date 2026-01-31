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

    public Map<String, String> generateHealthReport(List<org.ssafy.eeum.domain.health.entity.HealthMetric> metrics) {
        if (metrics.isEmpty()) {
            return Map.of(
                    "summary", "오늘 수집된 건강 데이터가 없습니다.",
                    "description", "사용자의 활동이나 생체 신호 데이터가 기록되지 않았습니다. 워치 연결 상태를 확인해주세요.");
        }

        // Prepare context for AI
        StringBuilder context = new StringBuilder(
                "당신은 전문 패밀리 헬스 어드바이저입니다. 아래의 건강 데이터를 분석하여 노인(피부양자)을 위한 따뜻하고 전문적인 리포트를 작성해주세요.\n\n");
        for (org.ssafy.eeum.domain.health.entity.HealthMetric m : metrics) {
            context.append(
                    String.format("- 시간: %s, 걸음수: %s, 평균심박수: %s, 혈압: %s/%s, 혈중산소: %s, 활동칼로리: %s kcal, 활동시간: %s분\n",
                            m.getRecordDate(), m.getSteps(), m.getAverageHeartRate(), m.getSystolicPressure(),
                            m.getDiastolicPressure(), m.getBloodOxygen(), m.getActiveCalories(), m.getActiveMinutes()));
        }
        context.append("\n응답은 반드시 아래 JSON 형식으로만 해주세요:\n{\"summary\": \"한 줄 요약\", \"description\": \"상세 분석 및 조언\"}");

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(apiUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "developer", "content", "당신은 건강 리포트 생성기입니다. JSON 형식으로만 응답하세요."),
                            Map.of("role", "user", "content", context.toString())));

            Map<String, Object> response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");

                    // Basic JSON extraction if content has markers
                    if (content.contains("```json")) {
                        content = content.substring(content.indexOf("```json") + 7);
                        content = content.substring(0, content.indexOf("```"));
                    }

                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    return mapper.readValue(content,
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {
                            });
                }
            }
        } catch (Exception e) {
            log.error("GMS Health Report Generation Error: {}", e.getMessage());
        }

        return Map.of(
                "summary", "리포트 생성 중 오류가 발생했습니다.",
                "description", "데이터 분석을 일시적으로 완료하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }

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

            Map<String, Object> response = webClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
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
