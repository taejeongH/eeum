package org.ssafy.eeum.domain.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "멤버 관계 수정 요청 DTO")
public class UpdateMemberRelationshipRequestDto {
    @Schema(description = "새로운 대표자와의 관계")
    private String relationship;
}
