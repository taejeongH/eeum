package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.iot.entity.IotDevice;

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
