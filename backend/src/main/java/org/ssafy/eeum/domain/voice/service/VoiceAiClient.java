package org.ssafy.eeum.domain.voice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.ssafy.eeum.domain.voice.dto.PythonTtsRequestDTO;
import org.ssafy.eeum.domain.voice.dto.PythonTtsResponseDTO;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class VoiceAiClient {

    private final RestTemplate restTemplate;

    @Value("${spring.ai-server.url}")
    private String AI_SERVER_URL;

    @Value("${spring.ai-server.key}")
    private String AI_SERVER_KEY;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    public VoiceAiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String generateTts(PythonTtsRequestDTO requestDto) {
        try {
            String ttsUrl = AI_SERVER_URL + "/api/v1/voice/generate";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", AI_SERVER_KEY);

            HttpEntity<PythonTtsRequestDTO> entity = new HttpEntity<>(requestDto, headers);
            PythonTtsResponseDTO response = restTemplate.postForObject(ttsUrl, entity, PythonTtsResponseDTO.class);

            if (response != null && "success".equals(response.getStatus())) {
                return response.getAudioUrl();
            }
            return null;
        } catch (Exception e) {
            log.error("AI 서버 TTS 생성 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    public String convertWav(String samplePath) {
        try {
            String convertUrl = AI_SERVER_URL + "/api/voice/convert";

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("s3_url", samplePath);
            requestBody.put("bucket_name", bucketName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", AI_SERVER_KEY);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            Map<String, Object> response = restTemplate.postForObject(convertUrl, entity, Map.class);

            if (response != null && "success".equals(response.get("status"))) {
                return (String) response.get("s3_key");
            }
            return null;
        } catch (Exception e) {
            log.error("AI 서버 WAV 변환 호출 실패: {}", e.getMessage());
            return null;
        }
    }
}
