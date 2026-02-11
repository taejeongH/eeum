package org.ssafy.eeum.domain.album.dto;

import lombok.*;

/**
 * S3 업로드를 위한 Presigned URL 응답 DTO입니다.
 * 
 * @summary Presigned URL 응답 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponseDTO {
    private String url;
    private String fileName;
}
