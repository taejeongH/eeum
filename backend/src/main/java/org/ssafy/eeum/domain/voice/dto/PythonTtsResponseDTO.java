package org.ssafy.eeum.domain.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PythonTtsResponseDTO {
    private String status;

    @JsonProperty("audio_url")
    private String audioUrl;
}
