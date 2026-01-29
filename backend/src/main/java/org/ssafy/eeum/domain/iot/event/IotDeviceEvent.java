package org.ssafy.eeum.domain.iot.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class IotDeviceEvent {

    private final String serialNumber;
    private final String type; // "UPDATE", "DELETE"
    private final String location;

    public static IotDeviceEvent updated(String serialNumber, String location) {
        return new IotDeviceEvent(serialNumber, "UPDATE", location);
    }

    public static IotDeviceEvent deleted(String serialNumber) {
        return new IotDeviceEvent(serialNumber, "DELETE", null);
    }
}
