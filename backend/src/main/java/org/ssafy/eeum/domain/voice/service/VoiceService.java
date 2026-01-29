package org.ssafy.eeum.domain.voice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.voice.dto.TtsRequestDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceSampleRequestDTO;
import org.ssafy.eeum.domain.voice.entity.VoiceSample;
import org.ssafy.eeum.domain.voice.entity.VoiceScript;
import org.ssafy.eeum.domain.voice.repository.VoiceModelRepository;
import org.ssafy.eeum.domain.voice.repository.VoiceSampleRepository;
import org.ssafy.eeum.domain.voice.repository.VoiceScriptRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.mqtt.MqttService;
import org.ssafy.eeum.global.infra.s3.S3Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceService {

    private final VoiceScriptRepository scriptRepository;
    private final VoiceSampleRepository sampleRepository;
    private final VoiceModelRepository modelRepository;
    private final S3Service s3Service;
    private final MqttService mqttService;
    private final RestTemplate restTemplate;
    private final org.ssafy.eeum.domain.iot.service.IotSyncService iotSyncService;

    @Value("${spring.ai-server.url}")
    private String AI_SERVER_URL;

    @Value("${spring.ai-server.key}")
    private String AI_SERVER_KEY;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    // 1. 대본 목록 조회
    public List<VoiceScript> getScripts() {
        return scriptRepository.findAll();
    }

    // 2. 업로드용 URL 발급 (samples/{userId}/{scriptId}.wav)
    public String getUploadUrl(Integer userId, Integer scriptId) {
        String fileName = String.format("samples/%d/script_%d.wav", userId, scriptId);
        return s3Service.generatePresignedUrl(fileName, "audio/wav");
    }

    // 3. 음성 샘플 정보 저장 및 5개 수집 시 학습 요청
    @Transactional
    public void saveSample(User user, VoiceSampleRequestDTO request) {
        VoiceScript script = scriptRepository.findById(request.getScriptId())
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        VoiceSample sample = VoiceSample.builder()
                .user(user)
                .voiceScript(script)
                .samplePath(request.getSamplePath())
                .durationSec(request.getDurationSec())
                .build();
        sampleRepository.save(sample);

        String originalPath = request.getSamplePath();
        if (!originalPath.toLowerCase().endsWith(".wav")) {
            log.info("WAV 형식이 아님을 감지: {}. AI 서버에 변환을 요청합니다.", originalPath);

            try {
                String convertUrl = AI_SERVER_URL + "/api/voice/convert";

                Map<String, String> requestBody = new HashMap<>();
                requestBody.put("s3_url", request.getSamplePath());
                requestBody.put("bucket_name", bucketName);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-API-Key", AI_SERVER_KEY);

                HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

                Map<String, Object> response = restTemplate.postForObject(convertUrl, entity, Map.class);

                if (response != null && "success".equals(response.get("status"))) {
                    String convertedS3Key = (String) response.get("s3_key");
                    log.info("AI 변환 성공: {}", convertedS3Key);
                    sample.updateSamplePath(convertedS3Key);
                }
            } catch (Exception e) {
                log.error("AI 변환 호출 실패: {}", e.getMessage());
            }
        }
    }

    // 4. TTS 생성 (URL 반환)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String createTtsUrl(Integer userId, String text) {
        List<VoiceSample> samples = sampleRepository.findAllByUserId(userId);
        if (samples.isEmpty()) {
            throw new CustomException(ErrorCode.ENTITY_NOT_FOUND, "학습된 음성 샘플이 없습니다.");
        }
        VoiceSample representativeSample = samples.get(0);

        try {
            String ttsUrl = AI_SERVER_URL + "/api/voice/tts";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            requestBody.put("sample_s3_url", representativeSample.getSamplePath());
            requestBody.put("sample_transcript", representativeSample.getVoiceScript().getContent());
            requestBody.put("user_id", userId);
            requestBody.put("bucket_name", bucketName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", AI_SERVER_KEY);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            Map<String, Object> response = restTemplate.postForObject(ttsUrl, entity, Map.class);

            if (response != null && "success".equals(response.get("status"))) {
                return (String) response.get("full_url");
            }
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "TTS 생성 실패");
        } catch (Exception e) {
            log.error("TTS 생성 호출 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "TTS 생성 중 오류가 발생했습니다.");
        }
    }

    // 5. TTS 생성 및 IoT 전달 (기존 유지용)
    @Transactional
    public void generateTts(Integer userId, TtsRequestDTO request) {
        String generatedUrl = createTtsUrl(userId, request.getText());

        String jsonPayload = String.format("{\"url\": \"%s\", \"text\": \"%s\"}", generatedUrl,
                request.getText());
        mqttService.sendToIot(request.getGroupId(), "voice", jsonPayload);

        // 새로운 음성 생성 알림
        iotSyncService.notifyUpdate(request.getGroupId(), "voice", 1);
    }
}
