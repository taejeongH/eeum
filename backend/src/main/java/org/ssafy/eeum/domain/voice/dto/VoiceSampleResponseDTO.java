package org.ssafy.eeum.domain.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.voice.entity.VoiceSample;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "음성 샘플 응답 DTO")
public class VoiceSampleResponseDTO {
    @Schema(description = "샘플 ID", example = "1")
    private Integer id;

    @Schema(description = "샘플 별명", example = "구수한 목소리")
    private String nickname;

    @Schema(description = "관련 대본 ID (대본 녹음인 경우)", example = "1")
    private Integer scriptId;

    @Schema(description = "샘플 생성일")
    private LocalDateTime createdAt;

    @Schema(description = "테스트 음성 URL", example = "https://s3...")
    private String testAudioUrl;

    @Schema(description = "테스트 음성 생성 상태", example = "COMPLETED")
    private String status;

    public static VoiceSampleResponseDTO from(VoiceSample sample) {
        return VoiceSampleResponseDTO.builder()
                .id(sample.getId())
                .nickname(sample.getNickname())
                .scriptId(sample.getVoiceScript() != null ? sample.getVoiceScript().getId() : null)
                .createdAt(sample.getCreatedAt())
                .testAudioUrl(sample.getTestAudioPath())
                .status(sample.getVoiceTask() != null ? sample.getVoiceTask().getStatus().name() : "COMPLETED")
                .build();
    }

    public static VoiceSampleResponseDTO from(VoiceSample sample, String testAudioUrl) {
        String status = "COMPLETED";
        if (sample.getVoiceTask() != null) {
            status = sample.getVoiceTask().getStatus().name();
        } else if (sample.getTestAudioPath() == null) {
            status = "NOT_STARTED";
        }

        return VoiceSampleResponseDTO.builder()
                .id(sample.getId())
                .nickname(sample.getNickname())
                .scriptId(sample.getVoiceScript() != null ? sample.getVoiceScript().getId() : null)
                .createdAt(sample.getCreatedAt())
                .testAudioUrl(testAudioUrl)
                .status(status)
                .build();
    }
}
