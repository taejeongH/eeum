package org.ssafy.eeum.domain.voice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.voice.dto.PythonTtsRequestDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceTaskStatusResponseDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceSampleRequestDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceSampleResponseDTO;
import org.ssafy.eeum.domain.voice.entity.VoiceSample;
import org.ssafy.eeum.domain.voice.entity.VoiceScript;
import org.ssafy.eeum.domain.voice.entity.VoiceTask;
import org.ssafy.eeum.domain.voice.repository.VoiceSampleRepository;
import org.ssafy.eeum.domain.voice.repository.VoiceScriptRepository;
import org.ssafy.eeum.domain.voice.repository.VoiceTaskRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.gms.GmsService;
import org.ssafy.eeum.global.infra.s3.S3Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoiceService {

    private final VoiceScriptRepository scriptRepository;
    private final VoiceSampleRepository sampleRepository;
    private final VoiceTaskRepository taskRepository;
    private final S3Service s3Service;
    private final VoiceAiClient voiceAiClient;
    private final GmsService gmsService;

    private static final List<String> TEST_QUOTES = List.of(
            "너나 잘하세요.",
            "계획이 다 있었구나.",
            "니가 가라, 하와이.",
            "묻고 더블로 가!",
            "어이가 없네.");

    // 1. 대본 목록 조회
    public List<VoiceScript> getScripts() {
        return scriptRepository.findAll();
    }

    // 2. 업로드용 URL 발급 (samples/{userId}/{UUID}.{extension})
    public String getUploadUrl(Integer userId, String extension) {
        String uuid = UUID.randomUUID().toString();
        String fileName = String.format("samples/%d/%s.%s", userId, uuid, extension);
        String contentType = getContentTypeByExtension(extension);
        return s3Service.generatePresignedUrl(fileName, contentType);
    }

    private String getContentTypeByExtension(String extension) {
        if (extension == null)
            return "audio/wav";
        return switch (extension.toLowerCase()) {
            case "m4a" -> "audio/x-m4a";
            case "mp3" -> "audio/mpeg";
            case "3gp" -> "audio/3gpp";
            case "webm" -> "audio/webm";
            case "ogg" -> "audio/ogg";
            default -> "audio/wav";
        };
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
        log.info("음성 샘플 정보 저장 완료: {}", request.getSamplePath());

        // 학습용 VoiceTask가 없으면 생성 (상태 추적용)
        List<VoiceTask> tasks = taskRepository.findByUserId(user.getId());
        boolean hasTrainingTask = tasks.stream().anyMatch(t -> t.getType() == VoiceTask.TaskType.TRAINING);

        if (!hasTrainingTask) {
            VoiceTask trainingTask = VoiceTask.builder()
                    .user(user)
                    .type(VoiceTask.TaskType.TRAINING)
                    .status(VoiceTask.TaskStatus.IN_QUEUE)
                    .build();
            taskRepository.save(trainingTask);
        }

        // 샘플 등록 후 자동으로 테스트 음성 생성 제안 (동기 방식으로 동작한다고 하셨으므로 바로 실행)
        generateTestAudio(sample);
    }

    // 3-1. 보이스 작업 상태 조회
    public VoiceTaskStatusResponseDTO getVoiceModelStatus(Integer userId) {
        List<VoiceSample> samples = sampleRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<VoiceSampleResponseDTO> sampleDtos = samples.stream()
                .map(sample -> {
                    String testUrl = null;
                    if (sample.getTestAudioPath() != null) {
                        testUrl = s3Service.getPresignedUrl(sample.getTestAudioPath());
                    }
                    return VoiceSampleResponseDTO.from(sample, testUrl);
                })
                .toList();

        List<VoiceTask> tasks = taskRepository.findByUserId(userId);
        VoiceTask lastTrainingTask = tasks.stream()
                .filter(t -> t.getType() == VoiceTask.TaskType.TRAINING)
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                .findFirst()
                .orElse(null);

        User user = samples.isEmpty() ? null : samples.get(0).getUser();

        return VoiceTaskStatusResponseDTO.of((long) samples.size(), lastTrainingTask, user, sampleDtos);
    }

    @Transactional
    public void generateTestAudio(VoiceSample sample) {
        try {
            String randomQuote = TEST_QUOTES.get((int) (Math.random() * TEST_QUOTES.size()));
            String refText = sample.getVoiceScript() != null
                    ? sample.getVoiceScript().getContent()
                    : sample.getTranscript();

            PythonTtsRequestDTO requestDto = PythonTtsRequestDTO.builder()
                    .userId(String.valueOf(sample.getUser().getId()))
                    .refWavKey(sample.getSamplePath())
                    .refText(refText)
                    .text(randomQuote)
                    .build();

            String audioUrl = voiceAiClient.generateTts(requestDto, null);

            if (audioUrl != null) {
                if (audioUrl.startsWith("http")) {
                    // 즉시 완료된 경우
                    String testAudioKey = extractS3Key(audioUrl);
                    sample.completeTts(testAudioKey);
                    log.info("[Test Audio] Sample {} created immediately: {}", sample.getId(), testAudioKey);
                } else {
                    // 지연되어 Job ID가 반환된 경우 (VoiceTask 생성)
                    log.info("[Test Audio] Sample {} delay, Job ID: {}", sample.getId(), audioUrl);
                    VoiceTask task = VoiceTask.builder()
                            .user(sample.getUser())
                            .type(VoiceTask.TaskType.SAMPLE)
                            .status(VoiceTask.TaskStatus.IN_QUEUE)
                            .jobId(audioUrl)
                            .build();
                    taskRepository.save(task);
                    sample.updateVoiceTask(task);
                }
            }
        } catch (Exception e) {
            log.error("샘플 {}의 테스트 음성 생성 실패: {}", sample.getId(), e.getMessage());
        }
    }

    public String extractS3Key(String url) {
        if (url == null || !url.contains(".com/")) {
            // URL 형식이 아니거나 작업 ID인 경우
            return null;
        }
        return url.substring(url.indexOf(".com/") + 5);
    }

    // 3-2. 대표 샘플 설정
    @Transactional
    public void setRepresentativeSample(Integer userId, Integer sampleId) {
        VoiceSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND));

        if (!sample.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
        }

        // 학습 작업이 없으면 하나 만들어줌 (상태 추적용)
        List<VoiceTask> tasks = taskRepository.findByUserId(userId);
        boolean hasTrainingTask = tasks.stream().anyMatch(t -> t.getType() == VoiceTask.TaskType.TRAINING);

        if (!hasTrainingTask) {
            VoiceTask newTask = VoiceTask.builder()
                    .user(sample.getUser())
                    .type(VoiceTask.TaskType.TRAINING)
                    .status(VoiceTask.TaskStatus.IN_QUEUE)
                    .build();
            taskRepository.save(newTask);
        }

        sample.getUser().updateRepresentativeSample(sample);
    }

    // 3-2.1 대표 샘플 조회
    public VoiceSampleResponseDTO getRepresentativeSample(Integer userId) {
        VoiceSample sample = sampleRepository.findTopByUserIdOrderByCreatedAtDesc(userId) // 임시 fallback 용이 아니면 User에서
                                                                                          // 직접
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND));

        // 실제 User 엔티티의 필드를 확인하도록 변경
        User user = sample.getUser();
        if (user.getRepresentativeSample() == null) {
            throw new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND);
        }

        sample = user.getRepresentativeSample();
        String testUrl = null;
        if (sample.getTestAudioPath() != null) {
            testUrl = s3Service.getPresignedUrl(sample.getTestAudioPath());
        }
        return VoiceSampleResponseDTO.from(sample, testUrl);
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

    // 3-4. 음성 샘플 삭제 (Hard Delete)
    @Transactional
    public void deleteSample(Integer userId, Integer sampleId) {
        VoiceSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND));

        if (!sample.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
        }

        // 대표 샘플인지 확인 (User 엔티티 확인)
        if (sample.getUser().getRepresentativeSample() != null &&
                sample.getUser().getRepresentativeSample().getId().equals(sampleId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE); // 대표 샘플은 삭제 불가
        }

        sampleRepository.delete(sample);
    }

    // 4. TTS 생성 (URL 반환) - 독립 트랜잭션 설정
    @Value("${eeum.webhook-url}")
    private String webhookBaseUrl;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String createTtsUrl(Integer userId, String text, Integer messageId) {
        log.debug("사용자 {}의 목소리 모델 및 대표 샘플을 조회합니다.", userId);

        PythonTtsRequestDTO requestDto = buildPythonTtsRequestDTO(userId, text);

        // 메시지 전송 시에는 웹후크를 사용하지 않고 폴링(TtsCleanupScheduler)으로 처리하도록 수정
        // String webhookUrl = (messageId != null) ? (webhookBaseUrl + "?messageId=" +
        // messageId) : null;
        String webhookUrl = null;
        log.info("[TTS] Webhook URL: {} (Disabled for polling flow)", webhookUrl);

        String audioUrl = voiceAiClient.generateTts(requestDto, webhookUrl);
        if (audioUrl == null) {
            log.error("[TTS] 생성 요청 실패 (messageId: {})", messageId);
            return null;
        }

        if (audioUrl.startsWith("http")) {
            return audioUrl;
        }

        // 지연된 경우 VoiceTask 생성 및 ID 반환
        VoiceTask task = VoiceTask.builder()
                .user(sampleRepository.findTopByUserIdOrderByCreatedAtDesc(userId).get().getUser()) // userId로 유저 조회
                .type(VoiceTask.TaskType.MESSAGE)
                .status(VoiceTask.TaskStatus.IN_QUEUE)
                .jobId(audioUrl)
                .build();
        taskRepository.save(task);
        return "TASK_ID:" + task.getId();
    }

    public String generateTtsSync(Integer userId, String text) {
        log.info("[TTS Sync Test] 사용자 {}의 TTS 동기 생성 요청 (텍스트: {})", userId, text);

        // 1. 초기 요청 (웹후크 없이)
        String initialResult = createTtsUrl(userId, text, null);

        // 2. 즉시 URL이 반환된 경우 (Warm Start)
        if (initialResult.startsWith("http")) {
            return initialResult;
        }

        // 3. Job ID가 반환된 경우 (Cold Start) - 폴링 시작
        String jobId = initialResult;
        log.info("[TTS Sync Test] Job ID {} 에 대한 폴링을 시작합니다.", jobId);

        int maxAttempts = 30; // 300초 (10초 * 30)
        for (int i = 0; i < maxAttempts; i++) {
            try {
                Thread.sleep(10000); // 10초 대기 (서버 부하 경감)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            String statusOrUrl = voiceAiClient.checkJobStatus(jobId);
            if (statusOrUrl == null || "FAILED".equals(statusOrUrl) || "ERROR".equals(statusOrUrl)) {
                log.error("[TTS Sync Test] 음성 생성 실패 (상태: {})", statusOrUrl);
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            if (statusOrUrl.startsWith("http")) {
                log.info("[TTS Sync Test] 음성 생성 완료: {}", statusOrUrl);
                return statusOrUrl;
            }

            log.debug("[TTS Sync Test] 생성 중... (상태: {}, 시도: {}/{})", statusOrUrl, i + 1, maxAttempts);
        }

        log.warn("[TTS Sync Test] 시간 초과 (Job ID: {})", jobId);
        throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    public String generateTtsAsync(Integer userId, String text) {
        log.info("[TTS Async Test] 사용자 {}의 TTS 비동기 생성 요청 (텍스트: {})", userId, text);

        PythonTtsRequestDTO requestDto = buildPythonTtsRequestDTO(userId, text);

        String webhookUrl = webhookBaseUrl + "/test";
        return voiceAiClient.generateTts(requestDto, webhookUrl);
    }

    private PythonTtsRequestDTO buildPythonTtsRequestDTO(Integer userId, String text) {
        List<VoiceSample> samples = sampleRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        if (samples.isEmpty()) {
            throw new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND);
        }
        User user = samples.get(0).getUser();

        if (user.getRepresentativeSample() == null) {
            user.updateRepresentativeSample(samples.get(0));
            log.debug("User {}'s representative sample set to ID {}", userId, samples.get(0).getId());
        }

        VoiceSample referenceSample = user.getRepresentativeSample();
        String refText = referenceSample.getVoiceScript() != null
                ? referenceSample.getVoiceScript().getContent()
                : referenceSample.getTranscript();

        return PythonTtsRequestDTO.builder()
                .userId(String.valueOf(userId))
                .refWavKey(referenceSample.getSamplePath())
                .refText(refText)
                .text(text)
                .build();
    }

    public String transcribeAudio(MultipartFile file) {
        return gmsService.transcribeAudio(file);
    }
}
