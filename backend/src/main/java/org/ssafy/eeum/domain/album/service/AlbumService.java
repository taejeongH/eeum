package org.ssafy.eeum.domain.album.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.album.dto.AlbumDTOs.*;
import org.ssafy.eeum.domain.album.entity.MediaAsset;
import org.ssafy.eeum.domain.album.repository.AlbumRepository;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.iot.service.IotSyncService;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.s3.S3Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final FamilyRepository familyRepository;
    private final S3Service s3Service;
    private final IotSyncService iotSyncService;

    // 0. 업로드용 Presigned URL 생성
    public PresignedUrlResponseDTO generateUploadUrl(String fileName, String contentType) {
        String uniqueFileName = "album/" + java.util.UUID.randomUUID() + "-" + fileName;
        String url = s3Service.generatePresignedUrl(uniqueFileName, contentType);
        return PresignedUrlResponseDTO.builder()
                .url(url)
                .fileName(uniqueFileName)
                .build();
    }

    // 1. 사진 등록
    @Transactional
    public void addPhoto(Integer familyId, User user, AlbumRequestDTO request) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        MediaAsset asset = MediaAsset.builder()
                .family(family)
                .uploader(user)
                .storageUrl(request.getStorageUrl())
                .takenAt(request.getTakenAt())
                .description(request.getDescription())
                .build();

        albumRepository.save(asset);

        // IoT 동기화 알림
        iotSyncService.notifyUpdate(familyId, "image", 1);
    }

    // 2. 가족별 사진 목록 조회
    public List<AlbumResponseDTO> getPhotos(Integer familyId) {
        return albumRepository.findAllByFamilyIdAndDeletedAtIsNull(familyId).stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // 3. 사진 수정
    @Transactional
    public void updatePhoto(Integer photoId, AlbumRequestDTO request) {
        MediaAsset asset = albumRepository.findById(photoId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        asset.update(request.getTakenAt(), request.getDescription());

        // IoT 동기화 알림
        iotSyncService.notifyUpdate(asset.getFamily().getId(), "image", 1);
    }

    // 4. 사진 삭제 (Soft Delete)
    @Transactional
    public void deletePhoto(Integer photoId) {
        MediaAsset asset = albumRepository.findById(photoId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        Integer familyId = asset.getFamily().getId();
        albumRepository.delete(asset); // SQLDelete에 의해 isSynced=false 처리됨

        // IoT 동기화 알림
        iotSyncService.notifyUpdate(familyId, "image", 1);
    }

    /**
     * IoT 기기용 동기화 데이터 조회 로직
     * 미동기화된(isSynced=false) 항목들을 찾아 추가/수정용과 삭제용으로 구분하여 반환
     */
    @Transactional
    public IotAlbumSyncResponseDTO syncForIot(Integer familyId) {
        List<MediaAsset> unsyncedAssets = albumRepository.findAllByFamilyIdAndIsSyncedFalse(familyId);

        List<AlbumSyncItemResponseDTO> addedItems = new ArrayList<>();
        List<Integer> deletedIds = new ArrayList<>();
        List<Integer> syncedIds = new ArrayList<>();

        for (MediaAsset asset : unsyncedAssets) {
            if (asset.getDeletedAt() != null) {
                // 삭제된 경우 ID만 전송
                deletedIds.add(asset.getId());
            } else {
                // 추가 혹은 수정된 경우 전체 정보 전송
                addedItems.add(AlbumSyncItemResponseDTO.builder()
                        .id(asset.getId())
                        .url(s3Service.getPresignedUrl(asset.getStorageUrl()))
                        .description(asset.getDescription())
                        .takenAt(asset.getTakenAt())
                        .build());
            }
            syncedIds.add(asset.getId());
        }

        // 동기화 완료 처리 (한 번에 업데이트)
        if (!syncedIds.isEmpty()) {
            albumRepository.markAsSynced(syncedIds);
        }

        return IotAlbumSyncResponseDTO.builder()
                .added(addedItems)
                .deleted(deletedIds)
                .build();
    }

    private AlbumResponseDTO convertToResponse(MediaAsset asset) {
        return AlbumResponseDTO.builder()
                .id(asset.getId())
                .storageUrl(s3Service.getPresignedUrl(asset.getStorageUrl()))
                .description(asset.getDescription())
                .takenAt(asset.getTakenAt())
                .uploaderName(asset.getUploader().getName())
                .createdAt(asset.getCreatedAt().toString())
                .build();
    }
}
