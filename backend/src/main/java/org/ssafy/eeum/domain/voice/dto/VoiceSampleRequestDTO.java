package org.ssafy.eeum.domain.voice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VoiceSampleRequestDTO {
    @io.swagger.v3.oas.annotations.media.Schema(description = "대본 ID (대본 보고 읽은 경우 필수, 프리토킹인 경우 제외)", example = "1", nullable = true)
    private Integer scriptId;

    @io.swagger.v3.oas.annotations.media.Schema(description = "S3에 업로드된 파일 경로 (Presigned URL 아님, 'samples/...' 경로)", example = "samples/1/script_1.wav")
    private String samplePath;

    @io.swagger.v3.oas.annotations.media.Schema(description = "녹음 파일 길이(초). 3초 이상 10초 이하 필수.", example = "5.4")
    private Double durationSec;

    @io.swagger.v3.oas.annotations.media.Schema(description = "녹음 내용 텍스트 (프리토킹인 경우 필수)", example = "오늘 날씨가 참 좋네요", nullable = true)
    private String transcript; // STT로 변환된 텍스트 (사용자 정의 녹음 시 사용)
}
