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

        // Log 저장 (ADD)
        saveLog(familyId, asset.getId(), ActionType.ADD);

        // IoT 동기화 알림
        iotSyncService.notifyUpdate(familyId, "image");
    }

    // 2. 가족별 사진 목록 조회 (최적화됨)
    public List<AlbumResponseDTO> getPhotos(Integer familyId, Integer userId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 해당 가족 그룹의 멤버인지 확인
        supporterRepository.findByUserAndFamily(user, family)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

        List<MediaAsset> assets = albumRepository.findAllByFamilyId(familyId);
        
        // Parallel stream을 사용하여 Presigned URL 생성 병렬 처리
        return assets.parallelStream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // 3. 사진 수정
    @Transactional
    public void updatePhoto(Integer photoId, AlbumRequestDTO request) {
        MediaAsset asset = albumRepository.findById(photoId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        asset.update(request.getTakenAt(), request.getDescription());

        // Log 저장 (UPDATE)
        saveLog(asset.getFamily().getId(), asset.getId(), ActionType.UPDATE);

        // IoT 동기화 알림
        iotSyncService.notifyUpdate(asset.getFamily().getId(), "image");
    }

    // 4. 사진 삭제 (Soft Delete)
    @Transactional
    public void deletePhoto(Integer photoId, Integer requesterUserId) {
        MediaAsset asset = albumRepository.findById(photoId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        Family family = asset.getFamily();

        // 요청자 권한 확인
        Supporter requesterSupporter = supporterRepository.findByUserAndFamily(
                userRepository.findById(requesterUserId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)),
                family
        ).orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

        // 권한 체크: 업로더 본인이거나 가족 대표자인 경우만 삭제 가능
        boolean isUploader = asset.getUploader().getId().equals(requesterUserId);
        boolean isRepresentative = requesterSupporter.isRepresentativeFlag();

        if (!isUploader && !isRepresentative) {
            throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
        }

        Integer familyId = family.getId();

        // Log 저장 (DELETE)
        saveLog(familyId, asset.getId(), ActionType.DELETE);

        albumRepository.delete(asset); // SQLDelete에 의해 isSynced=false 처리됨

        // IoT 동기화 알림
        iotSyncService.notifyUpdate(familyId, "image");
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
            // 하드 델리트 환경에서는 unsynced인데 DB에 있으면 추가/수정된 것임.
            // 삭제된 내역은 별도의 Log를 사용하거나 sync logic을 고도화해야 함.
            // 현재 요구사항에 맞춰 물리적 삭제로 진행하므로 deletedAt 체크 제거.
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
