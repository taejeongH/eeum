package org.ssafy.eeum.domain.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.ssafy.eeum.domain.album.entity.MediaLog;
import org.ssafy.eeum.domain.album.repository.AlbumRepository;
import org.ssafy.eeum.domain.album.repository.MediaLogRepository;

import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.iot.dto.IotSyncDto;
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;
import org.ssafy.eeum.domain.message.repository.MessageRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceLog;
import org.ssafy.eeum.domain.voice.repository.VoiceLogRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.mqtt.MqttService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.iot.dto.IotFamilyMemberDto;
import org.ssafy.eeum.domain.iot.event.IotSyncEvent;
import org.ssafy.eeum.global.infra.redis.RedisService;
import org.ssafy.eeum.global.infra.s3.S3Service;
import lombok.Getter;

import java.util.*;

/**
 * IoT 기기와 서버 간의 데이터 동기화를 담당하는 서비스 클래스입니다.
 * 앨범(이미지) 및 음성 메시지(보이스)의 증분 동기화 로직을 제공하며,
 * 데이터 변경 시 MQTT를 통해 기기에 실시간 업데이트 알림을 전송합니다.
 * 
 * @summary IoT 데이터 동기화 및 알림 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotSyncService {

    private final IotDeviceRepository iotDeviceRepository;
    private final MqttService mqttService;
    private final ObjectMapper objectMapper;
    private final MediaLogRepository mediaLogRepository;
    private final VoiceLogRepository voiceLogRepository;
    private final AlbumRepository albumRepository;
    private final MessageRepository messageRepository;
    private final S3Service s3Service;
    private final RedisService redisService;
    private final FamilyRepository familyRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 클라이언트의 마지막 동기화 시점 이후 변경된(추가/삭제) 데이터를 조회합니다.
     * 이미지(image) 또는 음성 메시지(voice) 종류별로 증분 데이터를 추출합니다.
     * 
     * @summary 증분 데이터 동기화 조회
     * @param familyId        가족 그룹 식별자
     * @param kind            동기화 대상 종류 ("image" 또는 "voice")
     * @param clientLastLogId 클라이언트가 보유한 마지막 로그 ID
     * @return 동기화 항목 및 가족 정보를 포함한 DTO
     */
    @Transactional(readOnly = true)
    public IotSyncDto getSyncData(Integer familyId, String kind, Integer clientLastLogId) {
        Family family = getValidatedFamily(familyId);
        Integer lastLogId = determineBaseLogId(family, kind, clientLastLogId);
        List<IotFamilyMemberDto> members = getFamilyMembers(familyId);

        if ("image".equalsIgnoreCase(kind)) {
            return syncImageAssets(familyId, lastLogId, members);
        }
        if ("voice".equalsIgnoreCase(kind)) {
            return syncVoiceMessages(familyId, lastLogId, members);
        }

        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 동기화 종류입니다: " + kind);
    }

    private Family getValidatedFamily(Integer familyId) {
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));
    }

    private Integer determineBaseLogId(Family family, String kind, Integer clientLastLogId) {
        if (clientLastLogId != null && clientLastLogId > 0) {
            return clientLastLogId;
        }
        return "image".equalsIgnoreCase(kind) ? family.getLastMediaLogId() : family.getLastVoiceLogId();
    }

    private IotSyncDto syncImageAssets(Integer familyId, Integer lastLogId, List<IotFamilyMemberDto> members) {
        List<MediaLog> logs = mediaLogRepository.findByGroupIdAndIdGreaterThan(familyId, lastLogId);
        if (logs.isEmpty()) {
            return createEmptySyncDto(lastLogId, members);
        }

        SyncTargetIds targetIds = extractImageTargetIds(logs, lastLogId);
        List<IotSyncDto.SyncItem> addedItems = fetchImageSyncItems(targetIds.getAddedIds());

        return createSyncResponse(addedItems, targetIds.getDeletedIds(), targetIds.getMaxLogId(), members);
    }

    private IotSyncDto syncVoiceMessages(Integer familyId, Integer lastLogId, List<IotFamilyMemberDto> members) {
        List<VoiceLog> logs = voiceLogRepository.findByGroupIdAndIdGreaterThan(familyId, lastLogId);
        if (logs.isEmpty()) {
            return createEmptySyncDto(lastLogId, members);
        }

        SyncTargetIds targetIds = extractVoiceTargetIds(logs, lastLogId);
        List<IotSyncDto.SyncItem> addedItems = fetchVoiceSyncItems(targetIds.getAddedIds());

        return createSyncResponse(addedItems, targetIds.getDeletedIds(), targetIds.getMaxLogId(), members);
    }

    private SyncTargetIds extractImageTargetIds(List<MediaLog> logs, Integer baseLogId) {
        Set<Integer> added = new HashSet<>();
        List<Integer> deleted = new ArrayList<>();
        int maxId = baseLogId;

        for (MediaLog log : logs) {
            maxId = Math.max(maxId, log.getId());
            Integer mediaId = log.getMediaId();
            if (log.getActionType() == ActionType.DELETE) {
                added.remove(mediaId);
                deleted.add(mediaId);
            } else {
                deleted.remove(mediaId);
                added.add(mediaId);
            }
        }
        return new SyncTargetIds(new ArrayList<>(added), deleted, maxId);
    }

    private SyncTargetIds extractVoiceTargetIds(List<VoiceLog> logs, Integer baseLogId) {
        Set<Integer> added = new HashSet<>();
        List<Integer> deleted = new ArrayList<>();
        int maxId = baseLogId;

        for (VoiceLog log : logs) {
            maxId = Math.max(maxId, log.getId());
            Integer voiceId = log.getVoiceId();
            if (log.getActionType() == ActionType.DELETE) {
                added.remove(voiceId);
                deleted.add(voiceId);
            } else {
                deleted.remove(voiceId);
                added.add(voiceId);
            }
        }
        return new SyncTargetIds(new ArrayList<>(added), deleted, maxId);
    }

    private List<IotSyncDto.SyncItem> fetchImageSyncItems(List<Integer> ids) {
        return albumRepository.findAllById(ids).stream()
                .map(asset -> IotSyncDto.SyncItem.builder()
                        .id(asset.getId())
                        .url(s3Service.getPresignedUrl(asset.getStorageUrl()))
                        .description(asset.getDescription())
                        .takenAt(asset.getTakenAt().toString())
                        .userId(asset.getUploader().getId())
                        .build())
                .toList();
    }

    private List<IotSyncDto.SyncItem> fetchVoiceSyncItems(List<Integer> ids) {
        return messageRepository.findAllById(ids).stream()
                .map(msg -> IotSyncDto.SyncItem.builder()
                        .id(msg.getId())
                        .url(msg.getVoiceUrl() != null ? s3Service.getPresignedUrl(msg.getVoiceUrl()) : null)
                        .description(msg.getContent())
                        .takenAt(msg.getCreatedAt().toString())
                        .userId(msg.getSender().getId())
                        .build())
                .toList();
    }

    private IotSyncDto createEmptySyncDto(Integer lastLogId, List<IotFamilyMemberDto> members) {
        return IotSyncDto.builder()
                .added(List.of())
                .deleted(List.of())
                .lastLogId(lastLogId)
                .members(members)
                .build();
    }

    private IotSyncDto createSyncResponse(List<IotSyncDto.SyncItem> added, List<Integer> deleted,
            Integer maxLogId, List<IotFamilyMemberDto> members) {
        return IotSyncDto.builder()
                .added(added)
                .deleted(deleted)
                .lastLogId(maxLogId)
                .members(members)
                .build();
    }

    /**
     * 동기화 대상 ID들을 관리하는 내부 헬퍼 클래스입니다.
     */
    @Getter
    @RequiredArgsConstructor
    private static class SyncTargetIds {
        private final List<Integer> addedIds;
        private final List<Integer> deletedIds;
        private final int maxLogId;
    }

    /**
     * 특정 가족 그룹에 속한 구성원들의 기본 사용자 정보 목록을 조회합니다.
     * 
     * @summary 가족 구성원 리스트 조회
     * @param familyId 가족 그룹 식별자
     * @return 가족 구성원 정보 DTO 리스트
     */
    public List<IotFamilyMemberDto> getFamilyMembers(Integer familyId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        return family.getSupporters().stream()
                .map(supporter -> {
                    User user = supporter.getUser();
                    return IotFamilyMemberDto.builder()
                            .userId(user.getId())
                            .name(user.getName())
                            .profileImageUrl(
                                    user.getProfileImage() != null ? s3Service.getPresignedUrl(user.getProfileImage())
                                            : null)
                            .build();
                })
                .toList();
    }

    /**
     * 데이터 업데이트 알림 요청 이벤트를 발행합니다.
     * 트랜잭션 커밋 후 실제 처리를 위해 이벤트를 발행합니다.
     * 
     * @summary 동기화 업데이트 알림 요청
     * @param familyId 가족 그룹 식별자
     * @param kind     데이터 종류 ("image", "voice")
     */
    public void notifyUpdate(Integer familyId, String kind) {
        eventPublisher.publishEvent(new IotSyncEvent(familyId, kind));
    }

    /**
     * 트랜잭션 커밋 후 실행되는 동기화 이벤트 핸들러입니다.
     * Redis에 활성 업데이트 내역을 기록하고 즉시 MQTT 알림을 발송합니다.
     * 
     * @summary 동기화 이벤트 처리 핸들러
     * @param event 동기화 정보가 포함된 애플리케이션 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSyncEvent(IotSyncEvent event) {
        Integer familyId = event.getFamilyId();
        String kind = event.getKind();

        String familyIdStr = familyId.toString();
        String countKey = "sync:family:" + familyIdStr + ":" + kind + ":count";
        String activeFamilyKey = "sync:active_families";
        String activeKindsKey = "sync:family:" + familyIdStr + ":kinds";

        // 1. 활성 가족 및 kind 목록에 추가
        redisService.addToSet(activeFamilyKey, familyIdStr);
        redisService.addToSet(activeKindsKey, kind);

        // 2. 카운트 증가
        redisService.increment(countKey);

        // 3. 즉시 전송
        sendBatchNotification(familyId, kind);
    }

    /**
     * 특정 가족 그룹에 속한 모든 IoT 기기에 업데이트 알림 MQTT 메시지를 발송합니다.
     * 
     * @summary 배치 알림 전송 (MQTT)
     * @param familyId 가족 그룹 식별자
     * @param kind     데이터 종류
     */
    private void sendBatchNotification(Integer familyId, String kind) {
        String familyIdStr = familyId.toString();
        String countKey = "sync:family:" + familyIdStr + ":" + kind + ":count";
        String timeKey = "sync:family:" + familyIdStr + ":" + kind + ":last_sent";
        String msgId = UUID.randomUUID().toString();

        // MQTT payload 구성
        Map<String, Object> payload = new HashMap<>();
        payload.put("msg_id", msgId);
        payload.put("kind", kind);

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            List<IotDevice> devices = iotDeviceRepository.findAllByFamilyId(familyId);

            for (IotDevice device : devices) {
                String topic = String.format("eeum/device/%s/update", device.getSerialNumber());
                mqttService.publish(topic, jsonPayload);
                log.info("[MQTT] 기기에 업데이트 알림 전송 - 시리얼: {}, 종류: {}, 메시지ID: {}",
                        device.getSerialNumber(), kind, msgId);
            }

            // 상태 초기화
            redisService.deleteData(countKey);
            redisService.setDataWithExpiration(timeKey, String.valueOf(System.currentTimeMillis()),
                    24 * 60 * 60 * 1000L);

        } catch (Exception e) {
            log.error("[오류] 배치 알림 전송 실패: {}", e.getMessage());
        }
    }

    /**
     * 주기적으로 동기화 대기 중인 데이터를 체크하여 미발송된 업데이트 알림이 있다면 전송합니다.
     * 10분마다 실행되며, 마지막 전송 후 1시간이 지났을 경우에만 전송합니다.
     * 
     * @summary 주기적 업데이트 누락 방지 스케줄러
     */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void checkPeriodicUpdates() {
        String activeFamilyKey = "sync:active_families";
        Set<Object> families = redisService.getSetMembers(activeFamilyKey);

        if (families == null || families.isEmpty())
            return;

        long now = System.currentTimeMillis();
        long oneHour = 60 * 60 * 1000L;

        for (Object famIdObj : families) {
            try {
                String familyIdStr = (String) famIdObj;
                Integer familyId = Integer.parseInt(familyIdStr);

                String activeKindsKey = "sync:family:" + familyIdStr + ":kinds";
                Set<Object> kinds = redisService.getSetMembers(activeKindsKey);

                if (kinds == null || kinds.isEmpty())
                    continue;

                for (Object kindObj : kinds) {
                    String kind = (String) kindObj;
                    String timeKey = "sync:family:" + familyIdStr + ":" + kind + ":last_sent";
                    String countKey = "sync:family:" + familyIdStr + ":" + kind + ":count";

                    String lastSentStr = redisService.getData(timeKey);
                    long lastSent = lastSentStr != null ? Long.parseLong(lastSentStr) : 0;

                    // 1시간 지났는지 확인
                    if (now - lastSent >= oneHour) {
                        String countStr = redisService.getData(countKey);
                        int count = countStr != null ? Integer.parseInt(countStr) : 0;

                        if (count > 0) {
                            sendBatchNotification(familyId, kind);
                        } else {
                            redisService.setDataWithExpiration(timeKey, String.valueOf(now), 24 * 60 * 60 * 1000L);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[오류] 가족({})의 주기적 업데이트 체크 중 에러 발생", famIdObj, e);
            }
        }
    }
}
