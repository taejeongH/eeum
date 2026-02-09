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
        private Integer uploaderUserId;
        private String createdAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IotAlbumSyncResponseDTO {
        
        private List<AlbumSyncItemResponseDTO> added;
        
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
        private String uploaderName;
        private String uploaderProfileImage;
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
