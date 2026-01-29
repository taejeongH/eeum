package org.ssafy.eeum.domain.album.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

public class AlbumDTOs {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlbumRequestDTO {
        private String storageUrl;
        private LocalDate takenAt;
        private String description;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlbumResponseDTO {
        private Integer id;
        private String storageUrl;
        private String description;
        private LocalDate takenAt;
        private String uploaderName;
        private String createdAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IotAlbumSyncResponseDTO {
        // 새로 추가되거나 수정된 사진들 (전체 데이터 포함)
        private List<AlbumSyncItemResponseDTO> added;
        // 삭제된 사진들의 ID 리스트
        private List<Integer> deleted;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlbumSyncItemResponseDTO {
        private Integer id;
        private String url;
        private String description;
        private LocalDate takenAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedUrlResponseDTO {
        private String url;
        private String fileName;
    }
}
