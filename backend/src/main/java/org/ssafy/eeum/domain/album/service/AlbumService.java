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
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.domain.album.entity.MediaLog;
import org.ssafy.eeum.domain.album.repository.MediaLogRepository;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.domain.family.repository.SupporterRepository;
import org.ssafy.eeum.domain.family.entity.Supporter;
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
    private final MediaLogRepository mediaLogRepository;
    private final UserRepository userRepository;
    private final SupporterRepository supporterRepository;

    // 업로드용 Presigned URL 생성
    public PresignedUrlResponseDTO generateUploadUrl(String fileName, String contentType) {
        String uniqueFileName = "album/" + java.util.UUID.randomUUID() + "-" + fileName;
        String url = s3Service.generatePresignedUrl(uniqueFileName, contentType);
        return PresignedUrlResponseDTO.builder()
                .url(url)
                .fileName(uniqueFileName)
                .build();
    }

    // 사진 등록
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
        saveLog(familyId, asset.getId(), ActionType.ADD);
        iotSyncService.notifyUpdate(familyId, "image");
    }

    // 가족별 사진 목록 조회
    public List<AlbumResponseDTO> getPhotos(Integer familyId, Integer userId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        supporterRepository.findByUserAndFamily(user, family)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

        List<MediaAsset> assets = albumRepository.findAllByFamilyId(familyId);

        return assets.parallelStream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // 사진 수정
    @Transactional
    public void updatePhoto(Integer photoId, AlbumRequestDTO request) {
        MediaAsset asset = albumRepository.findById(photoId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        asset.update(request.getTakenAt(), request.getDescription());
        saveLog(asset.getFamily().getId(), asset.getId(), ActionType.UPDATE);
        iotSyncService.notifyUpdate(asset.getFamily().getId(), "image");
    }

    // 사진 삭제
    @Transactional
    public void deletePhoto(Integer photoId, Integer requesterUserId) {
        MediaAsset asset = albumRepository.findById(photoId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        Family family = asset.getFamily();

        Supporter requesterSupporter = supporterRepository.findByUserAndFamily(
                userRepository.findById(requesterUserId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)),
                family
        ).orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

        boolean isUploader = asset.getUploader().getId().equals(requesterUserId);
        boolean isRepresentative = requesterSupporter.isRepresentativeFlag();

        if (!isUploader && !isRepresentative) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
        }

        Integer familyId = family.getId();
        saveLog(familyId, asset.getId(), ActionType.DELETE);
        albumRepository.delete(asset);
        iotSyncService.notifyUpdate(familyId, "image");
    }

     // IoT 기기용 동기화 데이터 조회 로직
    @Transactional
    public IotAlbumSyncResponseDTO syncForIot(Integer familyId) {
        List<MediaAsset> unsyncedAssets = albumRepository.findAllByFamilyIdAndIsSyncedFalse(familyId);

        List<AlbumSyncItemResponseDTO> addedItems = new ArrayList<>();
        List<Integer> deletedIds = new ArrayList<>();
        List<Integer> syncedIds = new ArrayList<>();

        for (MediaAsset asset : unsyncedAssets) {
            addedItems.add(AlbumSyncItemResponseDTO.builder()
                    .id(asset.getId())
                    .url(s3Service.getPresignedUrl(asset.getStorageUrl()))
                    .description(asset.getDescription())
                    .takenAt(asset.getTakenAt())
                    .uploaderName(asset.getUploader().getName())
                    .uploaderProfileImage(asset.getUploader().getProfileImage() != null
                            ? s3Service.getPresignedUrl(asset.getUploader().getProfileImage())
                            : null)
                    .build());
            syncedIds.add(asset.getId());
        }

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
                .uploaderUserId(asset.getUploader().getId())
                .createdAt(asset.getCreatedAt().toString())
                .build();
    }

    private void saveLog(Integer familyId, Integer mediaId, ActionType actionType) {
        MediaLog log = MediaLog.builder()
                .groupId(familyId)
                .mediaId(mediaId)
                .actionType(actionType)
                .build();
        mediaLogRepository.save(log);
    }
}
