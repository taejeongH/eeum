package org.ssafy.eeum.domain.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VoiceSampleRequestDTO {
    @Schema(description = "대본 ID (대본 보고 읽은 경우 필수, 프리토킹인 경우 제외)", example = "1", nullable = true)
    private Integer scriptId;

    @Schema(description = "S3에 업로드된 파일 경로 (Presigned URL 아님, 'samples/...' 경로)", example = "samples/1/script_1.wav")
    private String samplePath;

    @Schema(description = "녹음 파일 길이(초). 3초 이상 10초 이하 필수.", example = "5.4")
    private Double durationSec;

    @Schema(description = "녹음 내용 텍스트 (프리토킹인 경우 필수)", example = "오늘 날씨가 참 좋네요", nullable = true)
    private String transcript; 

    @NotBlank(message = "음성 샘플 별명은 필수입니다.")
    @Schema(description = "음성 샘플 별명 (예: 구수한 목소리)", example = "구수한 목소리", nullable = false)
    private String nickname;
}
