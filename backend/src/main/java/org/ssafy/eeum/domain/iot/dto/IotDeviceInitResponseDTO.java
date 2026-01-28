package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class IotDeviceInitResponseDTO {
    private String status;
    private String message;
    private String token;

    @JsonProperty("group_id")
    private Integer groupId;

    private String location;

    @JsonProperty("serial_number")
    private String serialNumber;

    private List<IotDeviceMqttDTO> devices;
}
