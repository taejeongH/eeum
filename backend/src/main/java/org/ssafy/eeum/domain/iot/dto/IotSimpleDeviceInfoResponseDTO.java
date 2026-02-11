package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.iot.entity.IotDevice;

/**
 * 기기 초기화 및 목록 조회 시 사용되는 요약된 기기 정보 DTO 클래스입니다.
 * 시리얼 번호, 별칭, 설치 위치 및 타입을 포함합니다.
 * 
 * @summary 기기 간략 정보 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IotSimpleDeviceInfoResponseDTO {
    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("device_name")
    private String deviceName;

    @JsonProperty("location_type")
    private String locationType;

    @JsonProperty("device_type")
    private String deviceType;

    public static IotSimpleDeviceInfoResponseDTO of(IotDevice device) {
        return IotSimpleDeviceInfoResponseDTO.builder()
                .serialNumber(device.getSerialNumber())
                .deviceName(device.getDeviceName())
                .locationType(device.getLocationType())
                .deviceType(device.getDeviceType())
                .build();
    }
}
