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

}
