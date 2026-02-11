package org.ssafy.eeum.domain.album.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.album.dto.*;
import org.ssafy.eeum.domain.album.service.AlbumService;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;

import org.ssafy.eeum.global.common.response.RestApiResponse;
import org.ssafy.eeum.global.config.swagger.SwaggerApiSpec;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.util.List;

@Tag(name = "가족 앨범", description = "가족 앨범 사진 관리 및 IoT 동기화 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;

    /**
     * S3 업로드용 Presigned URL을 발급받습니다.
     * 
     * @summary 업로드 URL 생성
     * @param fileName    원본 파일 이름
     * @param contentType 파일 타입 (MIME)
     * @return S3 Presigned URL 정보
     */
    @SwaggerApiSpec(summary = "업로드용 Presigned URL 발급", description = "S3에 사진을 직접 업로드하기 위한 Presigned URL을 발급받습니다.", successMessage = "Presigned URL 발급 성공")
    @GetMapping("/album/presigned-url")
    public RestApiResponse<PresignedUrlResponseDTO> getPresignedUrl(
            @RequestParam String fileName,
            @RequestParam String contentType) {
        return RestApiResponse.success(albumService.generateUploadUrl(fileName, contentType));
    }

    /**
     * 가족 앨범에 사진을 한 장 업로드합니다.
     * 
     * @summary 사진 등록
     * @param familyId    가족 식별자
     * @param userDetails 현재 로그인한 사용자 정보
     * @param request     사진 등록 정보 DTO
     * @return 등록 성공 메시지
     */
    @SwaggerApiSpec(summary = "사진 업로드", description = "가족 앨범에 사진을 업로드합니다.", successMessage = "사진 업로드 성공", errors = {
            ErrorCode.FAMILY_NOT_FOUND })
    @PostMapping("/families/{familyId}/album")
    public RestApiResponse<Void> uploadPhoto(
            @PathVariable Integer familyId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AlbumRequestDTO request) {
        albumService.addPhoto(familyId, userDetails.getUser(), request);
        return RestApiResponse.success("사진 업로드 성공");
    }

    /**
     * 가족 앨범에 여러 장의 사진을 한 번에 업로드합니다.
     * 
     * @summary 사진 다중 등록
     * @param familyId    가족 식별자
     * @param userDetails 현재 로그인한 사용자 정보
     * @param requests    사진 등록 정보 DTO 리스트
     * @return 다중 등록 성공 메시지
     */
    @SwaggerApiSpec(summary = "사진 다중 업로드", description = "가족 앨범에 여러 장의 사진을 한 번에 업로드합니다.", successMessage = "사진 다중 업로드 성공", errors = {
            ErrorCode.FAMILY_NOT_FOUND })
    @PostMapping("/families/{familyId}/album/bulk")
    public RestApiResponse<Void> uploadPhotos(
            @PathVariable Integer familyId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody List<AlbumRequestDTO> requests) {
        albumService.addPhotos(familyId, userDetails.getUser(), requests);
        return RestApiResponse.success("사진 다중 업로드 성공");
    }

    /**
     * 해당 가족의 모든 사진 목록을 조회합니다.
     * 
     * @summary 사진 목록 조회
     * @param familyId    가족 식별자
     * @param userDetails 현재 로그인한 사용자 정보
     * @return 앨범 사진 목록 응답 DTO 리스트
     */
    @SwaggerApiSpec(summary = "사진 목록 조회", description = "해당 가족의 모든 사진 목록을 조회합니다.", successMessage = "사진 목록 조회 성공")
    @GetMapping("/families/{familyId}/album")
    public RestApiResponse<List<AlbumResponseDTO>> getPhotos(
            @PathVariable Integer familyId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return RestApiResponse.success(albumService.getPhotos(familyId, userDetails.getId()));
    }

    /**
     * 등록된 사진의 상세 정보(날짜, 설명 등)를 수정합니다.
     * 
     * @summary 사진 정보 수정
     * @param photoId 사진 식별자
     * @param request 수정할 사진 정보 DTO
     * @return 수정 성공 메시지
     */
    @SwaggerApiSpec(summary = "사진 수정", description = "사진의 날짜나 설명을 수정합니다.", successMessage = "사진 수정 성공", errors = {
            ErrorCode.ENTITY_NOT_FOUND })
    @PatchMapping("/album/{photoId}")
    public RestApiResponse<Void> updatePhoto(
            @PathVariable Integer photoId,
            @RequestBody AlbumRequestDTO request) {
        albumService.updatePhoto(photoId, request);
        return RestApiResponse.success("사진 수정 성공");
    }

    /**
     * 특정 사진을 삭제합니다.
     * 
     * @summary 사진 삭제
     * @param photoId     사진 식별자
     * @param userDetails 현재 로그인한 사용자 정보
     * @return 삭제 성공 메시지
     */
    @SwaggerApiSpec(summary = "사진 삭제", description = "사진을 삭제합니다. (IoT 동기화 알림 포함)", successMessage = "사진 삭제 성공", errors = {
            ErrorCode.ENTITY_NOT_FOUND })
    @DeleteMapping("/album/{photoId}")
    public RestApiResponse<Void> deletePhoto(
            @PathVariable Integer photoId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        albumService.deletePhoto(photoId, userDetails.getId());
        return RestApiResponse.success("사진 삭제 성공");
    }
}
