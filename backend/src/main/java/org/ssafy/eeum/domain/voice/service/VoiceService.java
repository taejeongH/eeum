package org.ssafy.eeum.domain.voice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
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
import org.ssafy.eeum.global.infra.s3.S3Service;

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
    private final UserRepository userRepository; // Inject UserRepository
    private final S3Service s3Service;
    private final VoiceAiClient voiceAiClient;

    private static final List<String> TEST_QUOTES = List.of(
            "너나 잘하세요.",
            "계획이 다 있었구나.",
            "니가 가라, 하와이.",
            "묻고 더블로 가!",
            "어이가 없네.");

    // 대본 목록 조회
    public List<VoiceScript> getScripts() {
        return scriptRepository.findAll();
    }

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

        generateTestAudio(sample);
    }

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
                    String testAudioKey = extractS3Key(audioUrl);
                    sample.completeTts(testAudioKey);
                    log.info("[Test Audio] Sample {} created immediately: {}", sample.getId(), testAudioKey);
                } else {
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
            return null;
        }
        return url.substring(url.indexOf(".com/") + 5);
    }

    @Transactional
    public void setRepresentativeSample(Integer userId, Integer sampleId) {
        VoiceSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND));

        if (!sample.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
        }

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

    public VoiceSampleResponseDTO getRepresentativeSample(Integer userId) {
        VoiceSample sample = sampleRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND));

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

    @Transactional
    public void updateSampleNickname(Integer userId, Integer sampleId, String nickname) {
        VoiceSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND));

        if (!sample.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
        }

        sample.updateNickname(nickname);
    }

    @Transactional
    public void deleteSample(Integer userId, Integer sampleId) {
        VoiceSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new CustomException(ErrorCode.VOICE_SAMPLE_NOT_FOUND));

        if (!sample.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
        }

        if (sample.getUser().getRepresentativeSample() != null &&
                sample.getUser().getRepresentativeSample().getId().equals(sampleId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        sampleRepository.delete(sample);
    }

    @Value("${eeum.webhook-url}")
    private String webhookBaseUrl;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String createTtsUrl(Integer userId, String text, Integer messageId) {
        log.debug("사용자 {}의 목소리 모델 및 대표 샘플을 조회합니다.", userId);

        PythonTtsRequestDTO requestDto = buildPythonTtsRequestDTO(userId, text);
        String webhookUrl = webhookBaseUrl + "/tts?messageId=" + messageId;

        String audioUrl = voiceAiClient.generateTts(requestDto, webhookUrl);
        if (audioUrl == null) {
            log.error("[TTS] 생성 요청 실패 (messageId: {})", messageId);
            return null;
        }

        if (audioUrl.startsWith("http")) {
            return audioUrl;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        VoiceTask task = VoiceTask.builder()
                .user(user)
                .type(VoiceTask.TaskType.MESSAGE)
                .status(VoiceTask.TaskStatus.IN_QUEUE)
                .jobId(audioUrl)
                .build();
        taskRepository.save(task);
        return "TASK_ID:" + task.getId();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String generateTtsSync(Integer userId, String text) {
        log.info("[TTS Sync Test] 사용자 {}의 TTS 동기 생성 요청 (텍스트: {})", userId, text);

        String initialResult = createTtsUrl(userId, text, null);

        if (initialResult.startsWith("http")) {
            return initialResult;
        }

        String jobId = initialResult;
        VoiceTask task = null;

        if (initialResult.startsWith("TASK_ID:")) {
            try {
                int taskId = Integer.parseInt(initialResult.substring(8));
                task = taskRepository.findById(taskId).orElse(null);
                if (task != null && task.getJobId() != null) {
                    jobId = task.getJobId();
                    log.info("[TTS Sync Test] DB Task ID {} -> RunPod Job ID {}", taskId, jobId);
                }
            } catch (Exception e) {
                log.error("[TTS Sync Test] Task ID 파싱 실패: {}", initialResult, e);
            }
        }

        log.info("[TTS Sync Test] Job ID {} 에 대한 폴링을 시작합니다.", jobId);

        int maxAttempts = 30;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            String statusOrUrl = voiceAiClient.checkJobStatus(jobId);
            if (task != null) {
                task.incrementPollCount();
            }

            if (statusOrUrl == null || "FAILED".equals(statusOrUrl) || "ERROR".equals(statusOrUrl)) {
                log.error("[TTS Sync Test] 음성 생성 응답 대기/실패 (상태: {})", statusOrUrl);
                // 일시적 에러(null, ERROR) 및 진짜 실패(FAILED) 상황
                if ("FAILED".equals(statusOrUrl)) {
                    if (task != null) {
                        task.fail(VoiceTask.TaskStatus.FAILED);
                        taskRepository.save(task);
                    }
                    throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
                // null이나 ERROR는 통신 장애일 가능성이 높으므로 실패 처리하지 않고 계속 진행 (continue)
                continue;
            }

            if (statusOrUrl.startsWith("http")) {
                log.info("[TTS Sync Test] 음성 생성 완료: {}", statusOrUrl);
                if (task != null) {
                    task.updateResult(statusOrUrl);
                    taskRepository.save(task);
                }
                return statusOrUrl;
            }

            log.debug("[TTS Sync Test] 생성 중... (상태: {}, 시도: {}/{})", statusOrUrl, i + 1, maxAttempts);
        }

        log.warn("[TTS Sync Test] 시간 초과 (Job ID: {})", jobId);
        if (task != null) {
            task.fail(VoiceTask.TaskStatus.TIMEOUT);
            taskRepository.save(task);
        }
        throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    public String generateTtsAsync(Integer userId, String text) {
        log.info("[TTS Async Test] 사용자 {}의 TTS 비동기 생성 요청 (텍스트: {})", userId, text);

        PythonTtsRequestDTO requestDto = buildPythonTtsRequestDTO(userId, text);

        String webhookUrl = webhookBaseUrl + "/test";
        return voiceAiClient.generateTts(requestDto, webhookUrl);
    }

    private static final String DEFAULT_SAMPLE_PATH = "samples/5/13930c43-32ad-4a56-ad35-b18bddf75744.webm";
    private static final String DEFAULT_SAMPLE_TRANSCRIPT = "지금부터 자유대본 테스트를 하겠습니다. 아무 말이라도 해야 돼서 아무 말이라도 합니다.";

    private PythonTtsRequestDTO buildPythonTtsRequestDTO(Integer userId, String text) {
        List<VoiceSample> samples = sampleRepository.findAllByUserIdOrderByCreatedAtDesc(userId);

        String refWavKey;
        String refText;

        if (samples.isEmpty()) {
            // Fallback to Default Voice Model (Hardcoded to prevent DB lookup failure)
            refWavKey = DEFAULT_SAMPLE_PATH;
            refText = DEFAULT_SAMPLE_TRANSCRIPT;
            log.info("[TTS] User {} has no voice model. Using Default Sample (Fallback): {}", userId, refWavKey);
        } else {
            User user = samples.get(0).getUser();
            VoiceSample referenceSample;

            if (user.getRepresentativeSample() == null) {
                user.updateRepresentativeSample(samples.get(0));
                log.debug("User {}'s representative sample set to ID {}", userId, samples.get(0).getId());
                referenceSample = samples.get(0);
            } else {
                referenceSample = user.getRepresentativeSample();
            }

            refWavKey = referenceSample.getSamplePath();
            refText = referenceSample.getVoiceScript() != null
                    ? referenceSample.getVoiceScript().getContent()
                    : referenceSample.getTranscript();
        }

        return PythonTtsRequestDTO.builder()
                .userId(String.valueOf(userId))
                .refWavKey(refWavKey)
                .refText(refText)
                .text(text)
                .build();
    }
}
