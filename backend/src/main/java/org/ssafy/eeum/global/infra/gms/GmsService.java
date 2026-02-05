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

    public Map<String, Object> generateHealthReport(List<org.ssafy.eeum.domain.health.entity.HealthMetric> metrics) {
        if (metrics.isEmpty()) {
            return Map.of(
                    "summary", "오늘 수집된 건강 데이터가 없습니다.",
                    "description", List.of(Map.of("title", "데이터 부족", "content",
                            "사용자의 활동이나 생체 신호 데이터가 기록되지 않았습니다. 워치 연결 상태를 확인해주세요.", "type", "WARNING")));
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
        context.append("\n응답은 반드시 아래 JSON 형식으로만 해주세요. 마크다운 기호 없이 순수 JSON만 반환하세요:\n" +
                "{\n" +
                "  \"summary\": \"한 줄 요약\",\n" +
                "  \"description\": [\n" +
                "    { \"title\": \"어제와 이렇게 달라졌어요\", \"content\": \"어제 데이터와 비교한 변화(평균심박수, 활동량 등) 분석\", \"type\": \"TREND\" },\n"
                +
                "    { \"title\": \"오늘의 핵심 지표 해석\", \"content\": \"걸음수, 혈압, 산소포화도 등 주요 지표의 의미 해석\", \"type\": \"METRIC\" },\n"
                +
                "    { \"title\": \"주의 깊게 볼 점\", \"content\": \"이상 신호나 데이터 누락 구간에 대한 주의 사항\", \"type\": \"WARNING\" },\n"
                +
                "    { \"title\": \"생활 조언\", \"content\": \"활동 강도 조절, 수분 섭취 등 오늘의 행동 가이드\", \"type\": \"ADVICE\" },\n" +
                "    { \"title\": \"오늘의 대화 주제 추천\", \"content\": \"보호자가 피부양자에게 건넬 따뜻한 질문이나 칭찬\", \"type\": \"SOCIAL\" },\n"
                +
                "    { \"title\": \"내일을 위한 건강 미션\", \"content\": \"내일 실천하면 좋은 구체적인 목표나 측정 루틴\", \"type\": \"ACTION\" }\n"
                +
                "  ]\n" +
                "}");

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(apiUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "developer", "content", "당신은 건강 리포트 생성기입니다. 반드시 구조화된 JSON 형식으로만 응답해야 합니다."),
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
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                            });
                }
            }
        } catch (Exception e) {
            log.error("GMS Health Report Generation Error: {}", e.getMessage());
        }

        return Map.of(
                "summary", "리포트 생성 중 오류가 발생했습니다.",
                "description", List.of(Map.of("title", "분석 오류", "content", "데이터 분석을 일시적으로 완료하지 못했습니다. 잠시 후 다시 시도해주세요.",
                        "type", "WARNING")));
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
