package org.ssafy.eeum.domain.voice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.ssafy.eeum.domain.voice.dto.PythonTtsRequestDTO;

@Slf4j
@Component
public class VoiceAiClient {

    private final RestTemplate restTemplate;

    @Value("${spring.ai-server.url}")
    private String AI_SERVER_URL;

    @Value("${spring.ai-server.key}")
    private String AI_SERVER_KEY;

    @Value("${spring.ai-server.internal-key}")
    private String INTERNAL_KEY;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    public VoiceAiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String generateTts(PythonTtsRequestDTO requestDto, String webhookUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", AI_SERVER_KEY);
            headers.set("X-API-Key", INTERNAL_KEY);

            java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("input", requestDto);
            if (webhookUrl != null) {
                requestBody.put("webhook", webhookUrl);
            }

            HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restTemplate.postForObject(AI_SERVER_URL, entity,
                    java.util.Map.class);

            if (response != null) {
                String status = (String) response.get("status");
                log.info("[RunPod] Job {} status: {}", response.get("id"), status);

                if ("COMPLETED".equals(status)) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> output = (java.util.Map<String, Object>) response.get("output");
                    if (output != null && "success".equals(output.get("status"))) {
                        return (String) output.get("url");
                    }
                } else if ("IN_QUEUE".equals(status) || "IN_PROGRESS".equals(status)) {
                    // 웹후크가 설정되어 있다면 "PENDING", 아니면 Job ID 반환
                    return (webhookUrl != null) ? "PENDING" : (String) response.get("id");
                }
            }
            return null;
        } catch (Exception e) {
            log.error("AI 서버 TTS 생성 호출 실패: {}, URL: {}", e.getMessage(), AI_SERVER_URL);
            return null;
        }
    }

    public String checkJobStatus(String jobId) {
        try {
            String statusUrl = AI_SERVER_URL.replace("/run", "/status/") + jobId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", AI_SERVER_KEY);
            headers.set("X-API-Key", INTERNAL_KEY);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restTemplate
                    .exchange(statusUrl, org.springframework.http.HttpMethod.GET, entity, java.util.Map.class)
                    .getBody();

            if (response != null) {
                String status = (String) response.get("status");
                log.info("[RunPod] Job {} status check: {}", jobId, status);
                if ("COMPLETED".equals(status)) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> output = (java.util.Map<String, Object>) response.get("output");
                    if (output != null && "success".equals(output.get("status"))) {
                        return (String) output.get("url");
                    }
                    return "FAILED";
                }
                return status; // IN_QUEUE, IN_PROGRESS 등
            }
        } catch (Exception e) {
            log.error("AI 서버 상태 조회 실패 (Job {}): {}", jobId, e.getMessage());
        }
        return "ERROR";
    }

}
