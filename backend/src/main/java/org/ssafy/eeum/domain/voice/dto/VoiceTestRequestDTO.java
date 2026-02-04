package org.ssafy.eeum.domain.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "TTS 테스트 요청 DTO")
public class VoiceTestRequestDTO {
    @Schema(description = "변환할 텍스트 내용", example = "어르신, 약 드실 시간이에요. 잊지 말고 꼭 챙겨 드세요.")
    @NotBlank(message = "텍스트 내용은 필수입니다.")
    private String text;
}
