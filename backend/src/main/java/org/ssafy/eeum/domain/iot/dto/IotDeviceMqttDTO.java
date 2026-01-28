package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.iot.entity.IotDevice;

@Getter
@Builder
@AllArgsConstructor
public class IotDeviceMqttDTO {

    @JsonProperty("group_id")
    private Integer groupId;

    @JsonProperty("serial_number")
    private String serialNumber;

    @JsonProperty("location_type")
    private String locationType;

    public static IotDeviceMqttDTO of(IotDevice device) {
        return IotDeviceMqttDTO.builder()
                .groupId(device.getFamily().getId())
                .serialNumber(device.getSerialNumber())
                .locationType(device.getLocationType())
                .build();
    }
}
