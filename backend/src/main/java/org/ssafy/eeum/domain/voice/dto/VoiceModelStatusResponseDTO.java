package org.ssafy.eeum.domain.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.voice.entity.VoiceModel;

@Getter
@Builder
@Schema(description = "음성 모델 학습 상태 응답 DTO")
public class VoiceModelStatusResponseDTO {
    @Schema(description = "학습 상태 (TRAINING, COMPLETED, ERROR, NOT_STARTED)")
    private String status;

    @Schema(description = "수집된 음성 샘플 개수 (최소 5개 필요)", example = "3")
    private long sampleCount;

    @Schema(description = "모델 생성 여부", example = "true")
    private boolean isModelCreated;

    @Schema(description = "등록된 음성 샘플 목록")
    private java.util.List<VoiceSampleResponseDTO> samples;

    @Schema(description = "지정된 대표 샘플 ID (없으면 null)", example = "1")
    private Integer representativeSampleId;

    public static VoiceModelStatusResponseDTO of(long sampleCount, VoiceModel model,
            java.util.List<VoiceSampleResponseDTO> sampleDtos) {
        String status = "NOT_STARTED";
        boolean isCreated = false;
        Integer repId = null;

        if (model != null) {
            status = model.getStatus().name();
            isCreated = true;
            if (model.getRepresentativeSample() != null) {
                repId = model.getRepresentativeSample().getId();
            }
        }

        return VoiceModelStatusResponseDTO.builder()
                .status(status)
                .sampleCount(sampleCount)
                .isModelCreated(isCreated)
                .samples(sampleDtos)
                .representativeSampleId(repId)
                .build();
    }
}
