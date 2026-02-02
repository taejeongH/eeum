package org.ssafy.eeum.domain.iot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Schema(description = "IoT 스트리밍 URL 업데이트 요청 DTO")
public class IotStreamingUrlRequestDTO {

    @Schema(description = "새로운 스트리밍 URL", example = "rtsp://example.com/stream")
    private String streamingUrl;

    public IotStreamingUrlRequestDTO(String streamingUrl) {
        this.streamingUrl = streamingUrl;
    }
}
