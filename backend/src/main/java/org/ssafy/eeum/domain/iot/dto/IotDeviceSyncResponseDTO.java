package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class IotDeviceSyncResponseDTO {
    private String status;

    @JsonProperty("group_id")
    private Integer groupId;

    @JsonProperty("serial_number")
    private String serialNumber;

    private List<IotFamilyMemberDto> members;

    private List<IotSimpleDeviceInfoResponseDTO> devices;
}
