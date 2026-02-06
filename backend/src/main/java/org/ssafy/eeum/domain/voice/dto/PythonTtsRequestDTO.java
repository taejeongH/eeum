package org.ssafy.eeum.domain.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PythonTtsRequestDTO {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("ref_wav_key")
    private String refWavKey;

    @JsonProperty("ref_text")
    private String refText;

    @JsonProperty("text")
    private String text;
}
