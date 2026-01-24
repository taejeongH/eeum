package org.ssafy.eeum.domain.voice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TtsRequestDTO {
    private String text;
    private Integer groupId; // MQTT 전송을 위한 그룹 ID
}