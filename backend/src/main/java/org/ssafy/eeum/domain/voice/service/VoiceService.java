package org.ssafy.eeum.domain.voice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.ssafy.eeum.domain.user.entity.User;
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

import java.util.List;

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

        // 사용자의 샘플이 5개가 모였다면 파이썬 학습 API 호출 로직 추가 가능
    }

    // 4. TTS 생성 및 IoT 전달
    @Transactional
    public void generateTts(Integer userId, TtsRequestDTO request) {
        // 파이썬 TTS API 호출 (예시)
        // String pythonUrl = "http://python-ai-server/generate-tts";
        // TtsResponse response = restTemplate.postForObject(pythonUrl, requestData, TtsResponse.class);

        String generatedS3Url = "https://s3.../generated.wav"; // 파이썬 결과물이라 가정

        // MQTT 전송
        String jsonPayload = String.format("{\"url\": \"%s\", \"text\": \"%s\"}", generatedS3Url, request.getText());
        mqttService.sendToIot(request.getGroupId(), "voice", jsonPayload);
    }
}
