package org.ssafy.eeum.domain.album.dto;

import lombok.*;
import java.time.LocalDate;

/**
 * 앨범 사진 등록 요청을 위한 DTO입니다.
 * 
 * @summary 사진 등록 요청 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumRequestDTO {
    private String storageUrl;
    private LocalDate takenAt;
    private String description;
}
