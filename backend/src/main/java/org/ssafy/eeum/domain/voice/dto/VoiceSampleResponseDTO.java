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

    @Schema(description = "샘플 생성일")
    private LocalDateTime createdAt;

    public static VoiceSampleResponseDTO from(VoiceSample sample) {
        return VoiceSampleResponseDTO.builder()
                .id(sample.getId())
                .nickname(sample.getNickname())
                .createdAt(sample.getCreatedAt())
                .build();
    }
}
