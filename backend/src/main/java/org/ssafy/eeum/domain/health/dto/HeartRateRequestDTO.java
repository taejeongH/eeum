package org.ssafy.eeum.domain.health.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "심박수 저장 요청 DTO")
public class HeartRateRequestDTO {

    @NotNull(message = "최소 심박수는 필수입니다.")
    @Schema(description = "최소 심박수", example = "60")
    private Integer minRate;

    @NotNull(message = "최대 심박수는 필수입니다.")
    @Schema(description = "최대 심박수", example = "100")
    private Integer maxRate;

    @NotNull(message = "평균 심박수는 필수입니다.")
    @Schema(description = "평균 심박수", example = "80")
    private Integer avgRate;

    @NotNull(message = "측정 시간은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "측정 시간", example = "2024-02-04T12:00:00")
    private LocalDateTime measuredAt;

    @NotNull(message = "가족 ID는 필수입니다.")
    @Schema(description = "가족 ID", example = "1")
    private Integer familyId;

    @Schema(description = "관련 낙상 이벤트 ID (선택)", example = "1")
    private Integer relatedId;
}
