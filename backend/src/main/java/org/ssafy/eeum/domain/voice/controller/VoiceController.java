package org.ssafy.eeum.domain.voice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.message.service.MessageService;
import org.ssafy.eeum.domain.voice.dto.TtsRequestDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceModelStatusResponseDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceSampleRequestDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceSampleResponseDTO;
import org.ssafy.eeum.domain.voice.dto.VoiceTestRequestDTO;
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
        private final MessageService messageService;

        @SwaggerApiSpec(summary = "학습용 대본 목록 조회", description = "사용자의 목소리 복제 학습을 위해 제공되는 3~10초 분량의 대본 5개를 조회합니다.", successMessage = "대본 목록 조회 성공")
        @GetMapping("/scripts")
        public RestApiResponse<List<VoiceScript>> getScripts() {
                return RestApiResponse.success(voiceService.getScripts());
        }

        @SwaggerApiSpec(summary = "음성 샘플 업로드용 URL 발급", description = "S3에 음성 샘플을 직접 업로드하기 위한 Presigned URL을 발급합니다. UUID 기반의 파일명을 사용하며, 다양한 확장자(.wav, .m4a, .mp3 등)를 지원합니다.", successMessage = "Presigned URL 발급 성공", errors = {
                        ErrorCode.ENTITY_NOT_FOUND, ErrorCode.INTERNAL_SERVER_ERROR })
        @GetMapping("/presigned-url")
        public RestApiResponse<String> getUrl(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "wav") String extension) {
                return RestApiResponse.success(voiceService.getUploadUrl(userDetails.getId(), extension));
        }

        @SwaggerApiSpec(summary = "음성 샘플 메타데이터 저장", description = """
                        S3에 업로드된 음성 샘플의 정보를 DB에 저장합니다. 두 가지 경우가 있습니다.

                        1. **대본 보고 읽기**: `scriptId` 필수, `transcript` 생략 가능
                           - 예: `{ "scriptId": 1, "samplePath": "...", "durationSec": 5.0 }`

                        2. **프리토킹(자유 녹음)**: `scriptId` 제외(null), `transcript`(녹음 내용) 필수
                           - 예: `{ "samplePath": "...", "durationSec": 5.0, "transcript": "사랑해요" }`

                        **주의**: `durationSec`는 반드시 **3.0 이상 10.0 이하**여야 합니다. (어길 시 `VOICE003` 에러)
                        """, successMessage = "샘플 정보 저장 성공", errors = { ErrorCode.ENTITY_NOT_FOUND,
                        ErrorCode.INVALID_INPUT_VALUE, ErrorCode.INVALID_AUDIO_DURATION })
        @PostMapping("/samples")
        public RestApiResponse<Void> saveSample(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @Valid @RequestBody VoiceSampleRequestDTO request) {
                voiceService.saveSample(userDetails.getUser(), request);
                return RestApiResponse.success("샘플 정보가 저장되었습니다.");
        }

        @SwaggerApiSpec(summary = "사용자 맞춤형 TTS 생성 및 전송", description = "저장된 사용자의 음성 모델을 활용해 입력 텍스트를 TTS로 변환하고, 생성된 URL을 MQTT로 IoT 기기에 전송합니다.", successMessage = "TTS 생성 및 전송 요청 성공", errors = {
                        ErrorCode.ENTITY_NOT_FOUND, ErrorCode.INTERNAL_SERVER_ERROR })
        @PostMapping("/tts")
        public RestApiResponse<Void> generateTts(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @Valid @RequestBody TtsRequestDTO request) {
                messageService.generateTts(userDetails.getId(), request.getGroupId(), request.getText());
                return RestApiResponse.success("TTS 생성 및 전송 요청이 접수되었습니다. 백그라운드에서 처리됩니다.");
        }

        @SwaggerApiSpec(summary = "음성 모델 학습 상태 조회", description = "자신의 음성 모델 학습 상태와 수집된 샘플 개수, 전체 샘플 목록을 조회합니다.", successMessage = "상태 조회 성공")
        @GetMapping("/status")
        public RestApiResponse<VoiceModelStatusResponseDTO> getStatus(
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                VoiceModelStatusResponseDTO response = voiceService.getVoiceModelStatus(userDetails.getId());
                return RestApiResponse.success(response);
        }

        @SwaggerApiSpec(summary = "대표 음성 샘플 설정", description = "TTS 생성 시 사용할 대표 음성 샘플(Reference)을 지정합니다.", successMessage = "대표 샘플 설정 성공")
        @PostMapping("/representative")
        public RestApiResponse<Void> setRepresentativeSample(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam Integer sampleId) {
                voiceService.setRepresentativeSample(userDetails.getId(), sampleId);
                return RestApiResponse.success("대표 음성 샘플이 성공적으로 설정되었습니다.");
        }

        @SwaggerApiSpec(summary = "대표 음성 샘플 조회", description = "현재 설정된 대표 음성 샘플을 조회합니다.", successMessage = "대표 샘플 조회 성공", errors = {
                        ErrorCode.VOICE_MODEL_NOT_FOUND, ErrorCode.VOICE_SAMPLE_NOT_FOUND })
        @GetMapping("/representative")
        public RestApiResponse<VoiceSampleResponseDTO> getRepresentativeSample(
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return RestApiResponse.success(voiceService.getRepresentativeSample(userDetails.getId()));
        }

        @SwaggerApiSpec(summary = "음성 샘플 별명 수정", description = "이미 등록된 음성 샘플의 별명을 수정합니다.", successMessage = "별명 수정 성공")
        @PatchMapping("/samples/{sampleId}/nickname")
        public RestApiResponse<Void> updateSampleNickname(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Integer sampleId,
                        @RequestParam @NotBlank String nickname) {
                voiceService.updateSampleNickname(userDetails.getId(), sampleId, nickname);
                return RestApiResponse.success("음성 샘플의 별명이 성공적으로 수정되었습니다.");
        }

        @SwaggerApiSpec(summary = "음성 샘플 삭제", description = "음성 샘플을 삭제(Soft Delete)합니다. 단, 대표 샘플로 설정된 경우 삭제할 수 없습니다.", successMessage = "삭제 성공")
        @DeleteMapping("/samples/{sampleId}")
        public RestApiResponse<Void> deleteSample(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Integer sampleId) {
                voiceService.deleteSample(userDetails.getId(), sampleId);
                return RestApiResponse.success("음성 샘플이 성공적으로 삭제되었습니다.");
        }

        @SwaggerApiSpec(summary = "TTS 테스트 생성 (URL 반환)", description = "입력한 텍스트를 현재 설정된 대표 목소리로 TTS 변환하여 S3 URL을 즉시 반환합니다. (테스트용, 결과 대기 후 반환)", successMessage = "TTS 생성 성공")
        @PostMapping("/test-tts")
        public RestApiResponse<String> testTts(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestBody @Valid VoiceTestRequestDTO request) {
                String url = voiceService.generateTtsSync(userDetails.getId(), request.getText());
                return RestApiResponse.success(HttpStatus.OK, "TTS 변환이 완료되었습니다.", url);
        }
}