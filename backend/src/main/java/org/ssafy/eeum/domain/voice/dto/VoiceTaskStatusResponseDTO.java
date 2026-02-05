package org.ssafy.eeum.domain.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.voice.entity.VoiceTask;

@Getter
@Builder
@Schema(description = "보이스 작업 상태 응답 DTO")
public class VoiceTaskStatusResponseDTO {
    @Schema(description = "학습 상태 (TRAINING, COMPLETED, ERROR, NOT_STARTED)")
    private String status;

    @Schema(description = "수집된 음성 샘플 개수 (최소 5개 필요)", example = "3")
    private long sampleCount;

    @Schema(description = "모델 생성 여부", example = "true")
    private boolean isModelCreated;

    @Schema(description = "목소리 샘플 목록")
    private java.util.List<VoiceSampleResponseDTO> samples;

    @Schema(description = "대표 목소리 샘플 ID")
    private Integer representativeSampleId;

    @Schema(description = "현재 진행 중인 학습 작업 ID (없으면 null)")
    private String trainingJobId;

    public static VoiceTaskStatusResponseDTO of(long sampleCount, VoiceTask task,
            org.ssafy.eeum.domain.auth.entity.User user,
            java.util.List<VoiceSampleResponseDTO> sampleDtos) {
        String status = "NOT_STARTED";
        boolean isCreated = false;
        Integer repId = null;

        if (task != null) {
            status = task.getStatus().name();
            isCreated = (task.getStatus() == VoiceTask.TaskStatus.COMPLETED);
        }

        if (user != null && user.getRepresentativeSample() != null) {
            repId = user.getRepresentativeSample().getId();
        }

        return VoiceTaskStatusResponseDTO.builder()
                .status(status)
                .sampleCount(sampleCount)
                .isModelCreated(isCreated)
                .samples(sampleDtos)
                .representativeSampleId(repId)
                .trainingJobId(task != null ? task.getJobId() : null)
                .build();
    }
}
