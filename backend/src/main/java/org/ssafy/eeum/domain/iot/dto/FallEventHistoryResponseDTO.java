package org.ssafy.eeum.domain.iot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.iot.entity.FallEvent;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "낙상 이벤트 이력 응답 DTO")
public class FallEventHistoryResponseDTO {
    @Schema(description = "이벤트 ID", example = "1")
    private Integer id;

    @Schema(description = "심각도 (1: 일반, 2: 위급)", example = "2")
    private Integer severity;

    @Schema(description = "영상 URL", example = "https://s3...")
    private String videoUrl;

    @Schema(description = "상태", example = "EMERGENCY")
    private String statusType;

    @Schema(description = "자동 판단 신뢰도", example = "0.95")
    private Double confidence;

    @Schema(description = "판단 근거 (STT 내용)", example = "도와주세요")
    private String sttContent;

    @Schema(description = "발생 일시")
    private LocalDateTime createdAt;

    public static FallEventHistoryResponseDTO of(FallEvent event, String videoUrl) {
        return FallEventHistoryResponseDTO.builder()
                .id(event.getId())
                .severity(event.getSeverity())
                .videoUrl(videoUrl)
                .statusType(event.getStatusType().name())
                .confidence(event.getConfidence())
                .sttContent(event.getSttContent())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
