package org.ssafy.eeum.domain.voice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.voice.dto.TtsRequestDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceSampleRequestDTO;
import org.ssafy.eeum.domain.voice.entity.VoiceScript;
import org.ssafy.eeum.domain.voice.service.VoiceService;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.util.List;

@Tag(name = "Voice", description = "음성 복제 및 TTS API")
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {
    private final VoiceService voiceService;

    @SwaggerApiSpec(
            summary = "학습용 대본 목록 조회",
            description = "사용자의 목소리 복제 학습을 위해 제공되는 3~10초 분량의 대본 5개를 조회합니다.",
            successMessage = "대본 목록 조회 성공"
    )
    @GetMapping("/scripts")
    public RestApiResponse<List<VoiceScript>> getScripts() {
        return RestApiResponse.success(voiceService.getScripts());
    }

    @SwaggerApiSpec(
            summary = "음성 샘플 업로드용 URL 발급",
            description = "S3에 음성 샘플(.wav)을 직접 업로드하기 위한 Presigned URL을 발급합니다.",
            successMessage = "Presigned URL 발급 성공",
            errors = {ErrorCode.ENTITY_NOT_FOUND, ErrorCode.INTERNAL_SERVER_ERROR}
    )
    @GetMapping("/presigned-url")
    public RestApiResponse<String> getUrl(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Integer scriptId) {
        return RestApiResponse.success(voiceService.getUploadUrl(userDetails.getId(), scriptId));
    }

    @SwaggerApiSpec(
            summary = "음성 샘플 메타데이터 저장",
            description = "S3에 업로드된 음성 샘플의 경로(sample_path)와 재생 시간(duration_sec) 정보를 DB에 저장합니다.",
            successMessage = "샘플 정보 저장 성공",
            errors = {ErrorCode.ENTITY_NOT_FOUND, ErrorCode.INVALID_INPUT_VALUE}
    )
    @PostMapping("/samples")
    public RestApiResponse<Void> saveSample(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody VoiceSampleRequestDTO request) {
        voiceService.saveSample(userDetails.getUser(), request);
        return RestApiResponse.success("샘플 정보가 저장되었습니다.");
    }

    @SwaggerApiSpec(
            summary = "사용자 맞춤형 TTS 생성 및 전송",
            description = "저장된 사용자의 음성 모델을 활용해 입력 텍스트를 TTS로 변환하고, 생성된 URL을 MQTT로 IoT 기기에 전송합니다.",
            successMessage = "TTS 생성 및 전송 요청 성공",
            errors = {ErrorCode.ENTITY_NOT_FOUND, ErrorCode.INTERNAL_SERVER_ERROR}
    )
    @PostMapping("/tts")
    public RestApiResponse<Void> generateTts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TtsRequestDTO request) {
        voiceService.generateTts(userDetails.getId(), request);
        return RestApiResponse.success("TTS 생성 및 전송이 완료되었습니다.");
    }
}