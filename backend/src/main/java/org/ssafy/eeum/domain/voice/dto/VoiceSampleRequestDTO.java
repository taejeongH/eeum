package org.ssafy.eeum.domain.voice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VoiceSampleRequestDTO {
    private Integer scriptId;
    private String samplePath;
    private Double durationSec;
}
