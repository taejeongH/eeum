package org.ssafy.eeum.domain.iot.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class IotSyncEvent {
    private Integer familyId;
    private String kind;
}
