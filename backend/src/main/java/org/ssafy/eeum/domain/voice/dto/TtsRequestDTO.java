package org.ssafy.eeum.domain.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TtsRequestDTO {
    @Schema(description = "변환할 텍스트 내용", example = "밥 먹을 시간이야.")
    @NotBlank(message = "텍스트 내용은 필수입니다.")
    private String text;

    @Schema(description = "전송 대상 기기 그룹 ID", example = "1")
    @NotNull(message = "그룹 ID는 필수입니다.")
    private Long groupId;
}