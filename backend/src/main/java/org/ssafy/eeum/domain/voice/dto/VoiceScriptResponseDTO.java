package org.ssafy.eeum.domain.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VoiceScriptResponseDTO {
    private Integer id;
    private String content;
    private Integer scriptOrder;
}
