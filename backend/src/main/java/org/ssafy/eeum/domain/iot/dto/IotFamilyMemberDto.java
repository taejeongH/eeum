package org.ssafy.eeum.domain.iot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IotFamilyMemberDto {
    private Integer userId;
    private String name;
    private String profileImageUrl;
}
