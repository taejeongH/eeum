package org.ssafy.eeum.domain.album.dto;

import lombok.*;
import java.util.List;

/**
 * IoT 기기용 앨범 데이터 동기화 응답을 위한 DTO입니다.
 * 
 * @summary IoT 앨범 동기화 응답 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IotAlbumSyncResponseDTO {
    // 새로 추가되거나 수정된 사진들
    private List<AlbumSyncItemResponseDTO> added;
    // 삭제된 사진들의 ID 리스트
    private List<Integer> deleted;
}
