package org.ssafy.eeum.domain.voice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.voice.dto.PythonTtsRequestDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceModelStatusResponseDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceSampleRequestDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceSampleResponseDTO;
import org.ssafy.eeum.domain.voice.entity.VoiceModel;
import org.ssafy.eeum.domain.voice.entity.VoiceSample;
import org.ssafy.eeum.domain.voice.entity.VoiceScript;
import org.ssafy.eeum.domain.voice.repository.VoiceModelRepository;
import org.ssafy.eeum.domain.voice.repository.VoiceSampleRepository;
import org.ssafy.eeum.domain.voice.repository.VoiceScriptRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.s3.S3Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceService {

    private final VoiceScriptRepository scriptRepository;
    private final VoiceSampleRepository sampleRepository;
    private final VoiceModelRepository modelRepository;
    private final S3Service s3Service;
    private final VoiceAiClient voiceAiClient;

    // 1. 대본 목록 조회
    public List<VoiceScript> getScripts() {
        return scriptRepository.findAll();
    }

    // 2. 업로드용 URL 발급 (samples/{userId}/{scriptId}.wav)
    public String getUploadUrl(Integer userId, Integer scriptId) {
        String fileName = String.format("samples/%d/script_%d.wav", userId, scriptId);
        return s3Service.generatePresignedUrl(fileName, "audio/wav");
    }

    // 3. 음성 샘플 정보 저장
    @Transactional
    public void saveSample(User user, VoiceSampleRequestDTO request) {
        VoiceScript script = null;
        if (request.getScriptId() != null) {
            script = scriptRepository.findById(request.getScriptId())
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
        }

        if (script == null && (request.getTranscript() == null || request.getTranscript().trim().isEmpty())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        VoiceSample sample = VoiceSample.builder()
                .user(user)
                .voiceScript(script)
                .samplePath(request.getSamplePath())
                .durationSec(request.getDurationSec())
                .transcript(request.getTranscript())
                .nickname(request.getNickname())
                .build();

        if (request.getDurationSec() == null || request.getDurationSec() < 3.0 || request.getDurationSec() > 10.0) {
            throw new CustomException(ErrorCode.INVALID_AUDIO_DURATION);
        }

        sampleRepository.save(sample);

        String originalPath = request.getSamplePath();
        if (!originalPath.toLowerCase().endsWith(".wav")) {
            log.info("WAV 형식이 아님을 감지: {}. AI 서버에 변환을 요청합니다.", originalPath);
            String convertedS3Key = voiceAiClient.convertWav(originalPath);
            if (convertedS3Key != null) {
                log.info("AI 변환 성공: {}", convertedS3Key);
                sample.updateSamplePath(convertedS3Key);
            }
        }
    }

    // 3-1. 모델 학습 상태 조회
    public VoiceModelStatusResponseDTO getVoiceModelStatus(Integer userId) {
        List<VoiceSample> samples = sampleRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<VoiceSampleResponseDTO> sampleDtos = samples.stream()
                .map(VoiceSampleResponseDTO::from)
                .toList();

        VoiceModel model = modelRepository.findByUserId(userId).orElse(null);
        return VoiceModelStatusResponseDTO.of(samples.size(), model, sampleDtos);
    }

    // 3-2. 대표 샘플 설정
    @Transactional
    public void setRepresentativeSample(Integer userId, Integer sampleId) {
        VoiceSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND));

        if (!sample.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
        }

        VoiceModel model = modelRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_MODEL_NOT_FOUND));

        model.updateRepresentativeSample(sample);
    }

    // 3-3. 음성 샘플 별명 수정
    @Transactional
    public void updateSampleNickname(Integer userId, Integer sampleId, String nickname) {
        VoiceSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND));

        if (!sample.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
        }

        sample.updateNickname(nickname);
    }

    // 4. TTS 생성 (URL 반환) - 독립 트랜잭션 설정
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String createTtsUrl(Integer userId, String text) {
        log.debug("사용자 {}의 목소리 모델 및 대표 샘플을 조회합니다.", userId);

        VoiceModel voiceModel = modelRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("사용자 {}의 목소리 모델을 찾을 수 없습니다.", userId);
                    return new CustomException(ErrorCode.VOICE_MODEL_NOT_FOUND);
                });

        if (voiceModel.getGptPath() == null || voiceModel.getSovitsPath() == null) {
            log.warn("사용자 {}의 모델 경로(GptPath/SovitsPath)가 누락되었습니다.", userId);
            throw new CustomException(ErrorCode.VOICE_MODEL_NOT_FOUND);
        }

        // 대표 샘플이 지정되어 있으면 사용, 없으면 가장 최근 샘플 사용
        VoiceSample referenceSample = voiceModel.getRepresentativeSample();
        if (referenceSample == null) {
            List<VoiceSample> samples = sampleRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
            if (samples.isEmpty()) {
                throw new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND);
            }
            referenceSample = samples.get(0);
            log.debug("대표 샘플이 설정되지 않아 가장 최근 샘플(ID: {})을 사용합니다.", referenceSample.getId());
        } else {
            log.debug("설정된 대표 샘플(ID: {})을 사용합니다.", referenceSample.getId());
        }

        String refText = referenceSample.getVoiceScript() != null
                ? referenceSample.getVoiceScript().getContent()
                : referenceSample.getTranscript();

        PythonTtsRequestDTO requestDto = PythonTtsRequestDTO.builder()
                .userId(String.valueOf(userId))
                .gptKey(voiceModel.getGptPath())
                .sovitsKey(voiceModel.getSovitsPath())
                .refWavKey(referenceSample.getSamplePath())
                .refText(refText)
                .text(text)
                .build();

        String audioUrl = voiceAiClient.generateTts(requestDto);
        if (audioUrl != null) {
            return audioUrl;
        }
        throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
