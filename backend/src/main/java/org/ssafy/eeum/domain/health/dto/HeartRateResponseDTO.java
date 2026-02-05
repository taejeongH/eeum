package org.ssafy.eeum.domain.health.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Builder;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeartRateResponseDTO {
    private Double avgRate;
    private Integer minRate;
    private Integer maxRate;
    private Long sampleCount;
}
