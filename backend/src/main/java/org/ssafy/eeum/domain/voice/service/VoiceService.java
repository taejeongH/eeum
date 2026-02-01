package org.ssafy.eeum.domain.voice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation; // 추가됨
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.iot.service.IotSyncService;
import org.ssafy.eeum.domain.voice.dto.TtsRequestDTO;
import org.ssafy.eeum.domain.voice.dto.PythonTtsRequestDTO;
import org.ssafy.eeum.domain.voice.dto.PythonTtsResponseDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceSampleRequestDTO;
import org.ssafy.eeum.domain.voice.entity.VoiceSample;
import org.ssafy.eeum.domain.voice.entity.VoiceModel;
import org.ssafy.eeum.domain.voice.entity.VoiceScript;
import org.ssafy.eeum.domain.voice.repository.VoiceModelRepository;
import org.ssafy.eeum.domain.voice.repository.VoiceSampleRepository;
import org.ssafy.eeum.domain.voice.repository.VoiceScriptRepository;
import org.ssafy.eeum.domain.message.entity.Message;
import org.ssafy.eeum.domain.message.repository.MessageRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceLog;
import org.ssafy.eeum.domain.voice.repository.VoiceLogRepository;
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
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
    private final IotSyncService iotSyncService;
    private final MessageRepository messageRepository;
    private final VoiceLogRepository voiceLogRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;

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
        VoiceScript script = null;
        if (request.getScriptId() != null) {
            script = scriptRepository.findById(request.getScriptId())
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
        }

        // 대본이 없는 경우(프리토킹 등)에는 transcript가 필수입니다.
        if (script == null && (request.getTranscript() == null || request.getTranscript().trim().isEmpty())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        VoiceSample sample = VoiceSample.builder()
                .user(user)
                .voiceScript(script)
                .samplePath(request.getSamplePath())
                .durationSec(request.getDurationSec())
                .transcript(request.getTranscript())
                .build();

        // 녹음 길이 검증 (3초 이상 10초 이하)
        if (request.getDurationSec() == null || request.getDurationSec() < 3.0 || request.getDurationSec() > 10.0) {
            throw new CustomException(ErrorCode.INVALID_AUDIO_DURATION);
        }

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

    // 4. TTS 생성 (URL 반환) - 독립 트랜잭션 설정
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String createTtsUrl(Integer userId, String text) {
        // 가장 최신의 샘플을 사용 (정렬된 메서드 호출)
        List<VoiceSample> samples = sampleRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        if (samples.isEmpty()) {
            // ErrorCode에 맞는 메시지를 전달하거나 기본 생성자 활용
            throw new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND);
        }
        VoiceSample representativeSample = samples.get(0);

        VoiceModel voiceModel = modelRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_MODEL_NOT_FOUND));

        if (voiceModel.getGptPath() == null || voiceModel.getSovitsPath() == null) {
            throw new CustomException(ErrorCode.VOICE_MODEL_NOT_FOUND);
        }

        try {
            String ttsUrl = AI_SERVER_URL + "/api/v1/voice/generate";

            String refText = representativeSample.getVoiceScript() != null
                    ? representativeSample.getVoiceScript().getContent()
                    : representativeSample.getTranscript();

            PythonTtsRequestDTO requestDto = PythonTtsRequestDTO.builder()
                    .userId(String.valueOf(userId))
                    .gptKey(voiceModel.getGptPath())
                    .sovitsKey(voiceModel.getSovitsPath())
                    .refWavKey(representativeSample.getSamplePath())
                    .refText(refText)
                    .text(text)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", AI_SERVER_KEY);

            HttpEntity<PythonTtsRequestDTO> entity = new HttpEntity<>(requestDto, headers);
            PythonTtsResponseDTO response = restTemplate.postForObject(ttsUrl, entity, PythonTtsResponseDTO.class);

            if (response != null && "success".equals(response.getStatus())) {
                return response.getAudioUrl();
            }
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("TTS 생성 호출 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 5. TTS 생성 및 IoT 전달 (기존 유지용)
    @Transactional
    public void generateTts(Integer userId, TtsRequestDTO request) {
        String generatedUrl = createTtsUrl(userId, request.getText());

        // 1. Family & User 조회
        Family family = familyRepository.findById(request.getGroupId())
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 2. 메시지 저장
        Message message = Message.builder()
                .group(family)
                .sender(user)
                .content(request.getText())
                .voiceUrl(generatedUrl)
                .isRead(false)
                .isSynced(false)
                .build();
        messageRepository.save(message);

        // 3. 로그 저장 (ADD)
        VoiceLog log = VoiceLog.builder()
                .groupId(request.getGroupId())
                .voiceId(message.getId())
                .actionType(ActionType.ADD)
                .build();
        voiceLogRepository.save(log);

        // 4. MQTT 및 알림 전송
        String jsonPayload = String.format("{\"url\": \"%s\", \"text\": \"%s\"}", generatedUrl,
                request.getText());
        mqttService.sendToIot(request.getGroupId(), "voice", jsonPayload);
        iotSyncService.notifyUpdate(request.getGroupId(), "voice", 1);
    }
}