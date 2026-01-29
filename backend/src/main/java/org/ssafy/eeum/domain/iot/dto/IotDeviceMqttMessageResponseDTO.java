package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.iot.entity.IotDevice;

@Getter
@Builder
@AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class IotDeviceMqttMessageResponseDTO {

    @JsonProperty("group_id")
    private Integer groupId;

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("location_type")
    private String locationType;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("device_name")
    private String deviceName;

    public static IotDeviceMqttMessageResponseDTO of(IotDevice device) {
        return IotDeviceMqttMessageResponseDTO.builder()
                .groupId(device.getFamily().getId())
                .serialNumber(device.getSerialNumber())
                .locationType(device.getLocationType())
                .deviceName(device.getDeviceName())
                .build();
    }

    public static IotDeviceMqttMessageResponseDTO ofWithAuth(IotDevice device, String accessToken,
            String refreshToken) {
        return IotDeviceMqttMessageResponseDTO.builder()
                .groupId(device.getFamily().getId())
                .serialNumber(device.getSerialNumber())
                .locationType(device.getLocationType())
                .deviceName(device.getDeviceName())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
