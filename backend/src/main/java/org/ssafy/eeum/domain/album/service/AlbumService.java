package org.ssafy.eeum.domain.album.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.album.dto.*;
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

/**
 * 가족 앨범의 사진 관리 관련 비즈니스 로직을 처리하는 서비스 클래스입니다.
 */
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

        /**
         * S3 업로드용 Presigned URL을 생성합니다.
         * 
         * @summary 업로드 URL 생성
         * @param fileName    원본 파일 이름
         * @param contentType 파일 타입 (MIME)
         * @return S3 Presigned URL 및 생성된 파일명이 포함된 DTO
         */
        public PresignedUrlResponseDTO generateUploadUrl(String fileName, String contentType) {
                String uniqueFileName = "album/" + java.util.UUID.randomUUID() + "-" + fileName;
                String url = s3Service.generatePresignedUrl(uniqueFileName, contentType);
                return PresignedUrlResponseDTO.builder()
                                .url(url)
                                .fileName(uniqueFileName)
                                .build();
        }

        /**
         * 단일 사진을 앨범에 등록합니다.
         * 
         * @summary 사진 등록
         * @param familyId 가족 식별자
         * @param user     업로더(현재 사용자)
         * @param request  사진 정보 DTO
         */
        @Transactional
        public void addPhoto(Integer familyId, User user, AlbumRequestDTO request) {
                Family family = getFamilyOrThrow(familyId);
                MediaAsset asset = createMediaAsset(family, user, request);

                albumRepository.save(asset);
                saveLog(familyId, asset.getId(), ActionType.ADD);
                notifyIotUpdate(familyId);
        }

        /**
         * 여러 장의 사진을 앨범에 다중 등록합니다.
         * 
         * @summary 사진 다중 등록
         * @param familyId 가족 식별자
         * @param user     업로더(현재 사용자)
         * @param requests 사진 정보 DTO 리스트
         */
        @Transactional
        public void addPhotos(Integer familyId, User user, List<AlbumRequestDTO> requests) {
                Family family = getFamilyOrThrow(familyId);

                List<MediaAsset> assets = requests.stream()
                                .map(request -> createMediaAsset(family, user, request))
                                .collect(Collectors.toList());

                albumRepository.saveAll(assets);

                assets.forEach(asset -> saveLog(familyId, asset.getId(), ActionType.ADD));
                notifyIotUpdate(familyId);
        }

        /**
         * 가족별 사진 목록을 조회합니다.
         * 
         * @summary 사진 목록 조회
         * @param familyId 가족 식별자
         * @param userId   조회 요청자 ID
         * @return 앨범 사진 목록 응답 DTO 리스트
         */
        public List<AlbumResponseDTO> getPhotos(Integer familyId, Integer userId) {
                Family family = getFamilyOrThrow(familyId);
                User user = getUserOrThrow(userId);

                validateFamilyAccess(user, family);

                return albumRepository.findAllByFamilyId(familyId).stream()
                                .map(this::convertToResponse)
                                .collect(Collectors.toList());
        }

        /**
         * 등록된 사진 정보를 수정합니다.
         * 
         * @summary 사진 정보 수정
         * @param photoId 사진 식별자
         * @param request 수정할 정보 DTO
         */
        @Transactional
        public void updatePhoto(Integer photoId, AlbumRequestDTO request) {
                MediaAsset asset = getMediaAssetOrThrow(photoId);

                asset.update(request.getTakenAt(), request.getDescription());
                saveLog(asset.getFamily().getId(), asset.getId(), ActionType.UPDATE);
                notifyIotUpdate(asset.getFamily().getId());
        }

        /**
         * 사진을 삭제합니다. 업로더 혹은 대표 관리자 권한이 필요합니다.
         * 
         * @summary 사진 삭제
         * @param photoId         사진 식별자
         * @param requesterUserId 삭제 요청자 ID
         */
        @Transactional
        public void deletePhoto(Integer photoId, Integer requesterUserId) {
                MediaAsset asset = getMediaAssetOrThrow(photoId);
                Family family = asset.getFamily();
                User requester = getUserOrThrow(requesterUserId);

                validateDeletePermission(asset, requester, family);

                saveLog(family.getId(), asset.getId(), ActionType.DELETE);
                albumRepository.delete(asset);
                notifyIotUpdate(family.getId());
        }

        /**
         * IoT 기기용 동기화 데이터를 조회하고 동기화 상태로 표시합니다.
         * 
         * @summary IoT 데이터 동기화 조회
         * @param familyId 가족 식별자
         * @return 추가된 데이터 정보가 포함된 동기화 응답 DTO
         */
        @Transactional
        public IotAlbumSyncResponseDTO syncForIot(Integer familyId) {
                List<MediaAsset> unsyncedAssets = albumRepository.findAllByFamilyIdAndIsSyncedFalse(familyId);

                List<AlbumSyncItemResponseDTO> addedItems = convertToSyncItems(unsyncedAssets);
                updateSyncedStatus(unsyncedAssets);

                return IotAlbumSyncResponseDTO.builder()
                                .added(addedItems)
                                .deleted(new ArrayList<>())
                                .build();
        }

        private List<AlbumSyncItemResponseDTO> convertToSyncItems(List<MediaAsset> assets) {
                return assets.stream()
                                .map(this::convertToSyncItem)
                                .collect(Collectors.toList());
        }

        private void updateSyncedStatus(List<MediaAsset> assets) {
                List<Integer> syncedIds = assets.stream()
                                .map(MediaAsset::getId)
                                .collect(Collectors.toList());

                if (!syncedIds.isEmpty()) {
                        albumRepository.markAsSynced(syncedIds);
                }
        }

        // --- Helper Methods ---

        private Family getFamilyOrThrow(Integer familyId) {
                return familyRepository.findById(familyId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));
        }

        private User getUserOrThrow(Integer userId) {
                return userRepository.findById(userId)
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        }

        private MediaAsset getMediaAssetOrThrow(Integer photoId) {
                return albumRepository.findById(photoId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
        }

        private MediaAsset createMediaAsset(Family family, User user, AlbumRequestDTO request) {
                return MediaAsset.builder()
                                .family(family)
                                .uploader(user)
                                .storageUrl(request.getStorageUrl())
                                .takenAt(request.getTakenAt())
                                .description(request.getDescription())
                                .build();
        }

        private void validateFamilyAccess(User user, Family family) {
                supporterRepository.findByUserAndFamily(user, family)
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));
        }

        private void validateDeletePermission(MediaAsset asset, User requester, Family family) {
                Supporter supporter = supporterRepository.findByUserAndFamily(requester, family)
                                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));

                boolean isUploader = asset.getUploader().getId().equals(requester.getId());
                boolean isRepresentative = supporter.isRepresentativeFlag();

                if (!isUploader && !isRepresentative) {
                        throw new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS);
                }
        }

        private void notifyIotUpdate(Integer familyId) {
                iotSyncService.notifyUpdate(familyId, "image");
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

        private AlbumSyncItemResponseDTO convertToSyncItem(MediaAsset asset) {
                User uploader = asset.getUploader();
                return AlbumSyncItemResponseDTO.builder()
                                .id(asset.getId())
                                .url(s3Service.getPresignedUrl(asset.getStorageUrl()))
                                .description(asset.getDescription())
                                .takenAt(asset.getTakenAt())
                                .uploaderName(uploader.getName())
                                .uploaderProfileImage(uploader.getProfileImage() != null
                                                ? s3Service.getPresignedUrl(uploader.getProfileImage())
                                                : null)
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
