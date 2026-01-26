package org.ssafy.eeum.domain.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.ssafy.eeum.domain.iot.entity.IotDevice;

@Getter
@Builder
@AllArgsConstructor
public class IotDeviceResponseDTO {
    private Integer id;
    private Integer groupId;
    private String serialNumber;
    private String deviceName;
    private String locationType;
    private Boolean isActive;
    private String createdAt;

    public static IotDeviceResponseDTO of(IotDevice device) {
        return IotDeviceResponseDTO.builder()
                .id(device.getId())
                .groupId(device.getGroupId())
                .serialNumber(device.getSerialNumber())
                .deviceName(device.getDeviceName())
                .locationType(device.getLocationType())
                .isActive(device.getIsActive())
                .createdAt(device.getCreatedAt() != null ? device.getCreatedAt().toString() : null)
                .build();
    }
}
