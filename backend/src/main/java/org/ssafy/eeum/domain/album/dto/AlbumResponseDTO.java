package org.ssafy.eeum.domain.album.dto;

import lombok.*;
import java.time.LocalDate;

/**
 * 앨범 사진 상세 정보를 응답하기 위한 DTO입니다.
 * 
 * @summary 사진 상세 응답 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumResponseDTO {
    private Integer id;
    private String storageUrl;
    private String description;
    private LocalDate takenAt;
    private String uploaderName;
    private Integer uploaderUserId;
    private String createdAt;
}
