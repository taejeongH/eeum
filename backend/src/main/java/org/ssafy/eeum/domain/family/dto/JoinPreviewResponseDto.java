package org.ssafy.eeum.domain.family.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JoinPreviewResponseDto {
    private String familyName;
    private String inviterName;

    @Builder
    public JoinPreviewResponseDto(String familyName, String inviterName) {
        this.familyName = familyName;
        this.inviterName = inviterName;
    }
}
