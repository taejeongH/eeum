package org.ssafy.eeum.domain.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PythonTtsRequestDTO {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("gpt_key")
    private String gptKey;

    @JsonProperty("sovits_key")
    private String sovitsKey;

    @JsonProperty("ref_wav_key")
    private String refWavKey;

    @JsonProperty("ref_text")
    private String refText;

    @JsonProperty("text")
    private String text;
}
