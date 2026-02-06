package org.ssafy.eeum.global.infra.gms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

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

        public Map<String, Object> generateHealthReport(
                        List<org.ssafy.eeum.domain.health.entity.HealthMetric> metrics) {
                if (metrics.isEmpty()) {
                        return Map.of(
                                        "summary", "오늘 수집된 건강 데이터가 없습니다.",
                                        "description", List.of(Map.of("title", "데이터 부족", "content",
                                                        "사용자의 활동이나 생체 신호 데이터가 기록되지 않았습니다. 워치 연결 상태를 확인해주세요.", "type",
                                                        "WARNING")));
                }

                // Prepare context for AI
                StringBuilder context = new StringBuilder(
                                "당신은 전문 패밀리 헬스 어드바이저입니다. 아래의 건강 데이터를 분석하여 노인(피부양자)을 위한 따뜻하고 전문적인 리포트를 작성해주세요.\n\n");
                for (org.ssafy.eeum.domain.health.entity.HealthMetric m : metrics) {
                        context.append(
                                        String.format("- 시간: %s, 걸음수: %s, 평균심박수: %s, 혈압: %s/%s, 혈중산소: %s, 활동칼로리: %s kcal, 활동시간: %s분\n",
                                                        m.getRecordDate(), m.getSteps(), m.getAverageHeartRate(),
                                                        m.getSystolicPressure(),
                                                        m.getDiastolicPressure(), m.getBloodOxygen(),
                                                        m.getActiveCalories(), m.getActiveMinutes()));
                }
                context.append("\n응답은 반드시 아래 JSON 형식으로만 해주세요. 각 항목의 내용은 핵심만 1~2문장으로 매우 간결하게 작성하세요. 마크다운 기호 없이 순수 JSON만 반환하세요:\n"
                                +
                                "{\n" +
                                "  \"summary\": {\n" +
                                "    \"text\": \"오늘의 전체 상태 한 줄 요약\",\n" +
                                "    \"emoji\": \"😊(좋음), 😐(보통), ⚠️(주의) 중 하나\",\n" +
                                "    \"score\": 0~100 사이의 건강 점수\n" +
                                "  },\n" +
                                "  \"description\": [\n" +
                                "    { \"title\": \"지표 분석\", \"content\": \"어제와 비교한 변화 및 주요 지표 특징 (최대 2문장)\", \"type\": \"METRIC\" },\n"
                                +
                                "    { \"title\": \"주의 및 조언\", \"content\": \"주의할 점과 오늘의 행동 가이드 (최대 2문장)\", \"type\": \"WARNING\" },\n"
                                +
                                "    { \"title\": \"가족 소통\", \"content\": \"오늘 부모님께 건넬 따뜻한 질문이나 칭찬 (최대 1문장)\", \"type\": \"SOCIAL\" },\n"
                                +
                                "    { \"title\": \"내일의 미션\", \"content\": \"내일 실천할 구체적인 목표 하나 (최대 1문장)\", \"type\": \"ACTION\" }\n"
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
                                                        Map.of("role", "developer", "content",
                                                                        "당신은 건강 리포트 생성기입니다. 반드시 구조화된 JSON 형식으로만 응답해야 합니다."),
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
                                        Map<String, Object> message = (Map<String, Object>) choices.get(0)
                                                        .get("message");
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
                        log.error("GMS 건강 리포트 생성 오류: {}", e.getMessage());
                }

                return Map.of(
                                "summary", "리포트 생성 중 오류가 발생했습니다.",
                                "description",
                                List.of(Map.of("title", "분석 오류", "content", "데이터 분석을 일시적으로 완료하지 못했습니다. 잠시 후 다시 시도해주세요.",
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
                                        "response_format", Map.of("type", "json_object"),
                                        "messages", List.of(
                                                        Map.of("role", "developer", "content",
                                                                        "당신은 노인 낙상 사고 대응 전문가입니다. 기기가 사용자에게 '지금은 괜찮으세요? 도와드릴까요?'라고 물어본 상황입니다.\n"
                                                                                        +
                                                                                        "사용자의 답변을 분석하여 위급 상황(EMERGENCY)인지 안전한 상황(SAFE)인지 판단하세요.\n"
                                                                                        +
                                                                                        "반드시 아래 JSON 형식으로 응답하세요:\n"
                                                                                        +
                                                                                        "{\n"
                                                                                        +
                                                                                        "  \"status\": \"EMERGENCY\" 또는 \"SAFE\",\n"
                                                                                        +
                                                                                        "  \"reason\": \"판단 이유 (한 문장)\"\n"
                                                                                        +
                                                                                        "}\n"
                                                                                        +
                                                                                        "특히 '아니'라는 답변은 문맥에 따라 거절(SAFE)일 수도, 부정(EMERGENCY)일 수도 있으니 신중히 판단하세요."),
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
                                        Map<String, Object> message = (Map<String, Object>) choices.get(0)
                                                        .get("message");
                                        String content = (String) message.get("content");


                                        if (content == null)
                                                return false;

                                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                        JsonNode rootNode = mapper.readTree(content);
                                        String status = rootNode.path("status").asText().toUpperCase();



                                        return "EMERGENCY".equals(status);
                                }
                        }
                } catch (Exception e) {
                        log.error("GMS 감성 분석 오류: {}", e.getMessage(), e);
                }
                return false;
        }
}
