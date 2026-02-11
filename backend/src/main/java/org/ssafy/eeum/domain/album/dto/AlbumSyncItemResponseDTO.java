package org.ssafy.eeum.domain.album.dto;

import lombok.*;
import java.time.LocalDate;

/**
 * IoT 동기화 항목 하나에 대한 상세 정보를 포함하는 DTO입니다.
 * 
 * @summary IoT 동기화 항목 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumSyncItemResponseDTO {
    private Integer id;
    private String url;
    private String description;
    private LocalDate takenAt;
    private String uploaderName;
    private String uploaderProfileImage;
}
