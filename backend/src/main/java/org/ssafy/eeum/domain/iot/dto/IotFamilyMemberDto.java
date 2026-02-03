package org.ssafy.eeum.domain.iot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IotFamilyMemberDto {
    @JsonProperty("user_id")
    private Integer userId;
    private String name;
    @JsonProperty("profile_image_url")
    private String profileImageUrl;
}
