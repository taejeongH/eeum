package org.ssafy.eeum.domain.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.ssafy.eeum.domain.album.entity.MediaAsset;
import org.ssafy.eeum.domain.album.entity.MediaLog;
import org.ssafy.eeum.domain.album.repository.AlbumRepository;
import org.ssafy.eeum.domain.album.repository.MediaLogRepository;

import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.iot.dto.IotSyncDto;
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;
import org.ssafy.eeum.domain.message.entity.Message;
import org.ssafy.eeum.domain.message.repository.MessageRepository;
import org.ssafy.eeum.domain.voice.entity.VoiceLog;
import org.ssafy.eeum.domain.voice.repository.VoiceLogRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.mqtt.MqttService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ssafy.eeum.global.infra.redis.RedisService;
import org.ssafy.eeum.global.infra.s3.S3Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public IotSyncDto getSyncData(Integer familyId, String kind, Integer clientLastLogId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        Integer lastLogId;
        if (clientLastLogId != null && clientLastLogId > 0) {
            lastLogId = clientLastLogId;
        } else {
            lastLogId = "image".equals(kind) ? family.getLastMediaLogId() : family.getLastVoiceLogId();
        }

        List<Integer> deletedIds = new java.util.ArrayList<>();
        Map<Integer, Integer> addedMap = new HashMap<>();

        List<org.ssafy.eeum.domain.iot.dto.IotFamilyMemberDto> members = getFamilyMembers(familyId);

        if ("image".equals(kind)) {
            List<MediaLog> logs = mediaLogRepository
                    .findByGroupIdAndIdGreaterThan(familyId, lastLogId);
            if (logs.isEmpty())
                return IotSyncDto.builder()
                        .added(List.of())
                        .deleted(List.of())
                        .lastLogId(lastLogId)
                        .members(members)
                        .build();

            int maxLogId = lastLogId;
            for (var log : logs) {
                maxLogId = Math.max(maxLogId, log.getId());
                if (log.getActionType() == ActionType.DELETE) {
                    addedMap.remove(log.getMediaId());
                    deletedIds.add(log.getMediaId());
                } else {
                    if (deletedIds.contains(log.getMediaId())) {
                        deletedIds.remove(log.getMediaId());
                    }
                    addedMap.put(log.getMediaId(), log.getId());
                }
            }

            List<IotSyncDto.SyncItem> addedItems = new java.util.ArrayList<>();
            List<MediaAsset> assets = albumRepository.findAllById(addedMap.keySet());

            for (var asset : assets) {
                addedItems.add(IotSyncDto.SyncItem.builder()
                        .id(asset.getId())
                        .url(s3Service.getPresignedUrl(asset.getStorageUrl()))
                        .description(asset.getDescription())
                        .takenAt(asset.getTakenAt().toString())
                        .userId(asset.getUploader().getId())
                        .build());
                addedMap.remove(asset.getId());
            }
            return IotSyncDto.builder()
                    .added(addedItems)
                    .deleted(deletedIds)
                    .lastLogId(maxLogId)
                    .members(members)
                    .build();

        } else if ("voice".equals(kind)) {
            List<VoiceLog> logs = voiceLogRepository
                    .findByGroupIdAndIdGreaterThan(familyId, lastLogId);
            if (logs.isEmpty())
                return IotSyncDto.builder()
                        .added(List.of())
                        .deleted(List.of())
                        .lastLogId(lastLogId)
                        .members(members)
                        .build();

            int maxLogId = lastLogId;
            for (var log : logs) {
                maxLogId = Math.max(maxLogId, log.getId());
                if (log.getActionType() == ActionType.DELETE) {
                    addedMap.remove(log.getVoiceId());
                    deletedIds.add(log.getVoiceId());
                } else {
                    if (deletedIds.contains(log.getVoiceId())) {
                        deletedIds.remove(log.getVoiceId());
                    }
                    addedMap.put(log.getVoiceId(), log.getId());
                }
            }

            List<IotSyncDto.SyncItem> addedItems = new java.util.ArrayList<>();
            List<Message> messages = messageRepository
                    .findAllById(addedMap.keySet());

            for (var msg : messages) {
                addedItems.add(IotSyncDto.SyncItem.builder()
                        .id(msg.getId())
                        .url(msg.getVoiceUrl() != null ? s3Service.getPresignedUrl(msg.getVoiceUrl()) : null)
                        .description(msg.getContent())
                        .takenAt(msg.getCreatedAt().toString())
                        .userId(msg.getSender().getId())
                        .build());
            }

            return IotSyncDto.builder()
                    .added(addedItems)
                    .deleted(deletedIds)
                    .lastLogId(maxLogId)
                    .members(members)
                    .build();
        }

        return null;
    }

    public List<org.ssafy.eeum.domain.iot.dto.IotFamilyMemberDto> getFamilyMembers(Integer familyId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        return family.getSupporters().stream()
                .map(supporter -> {
                    org.ssafy.eeum.domain.auth.entity.User user = supporter.getUser();
                    return org.ssafy.eeum.domain.iot.dto.IotFamilyMemberDto.builder()
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
     * 데이터 업데이트 알림 요청 (즉시 전송)
     * 1. Redis에 활성 가족 및 kind 목록 추가
     * 2. 즉시 MQTT 전송 (1개라도 생기면 바로 보냄)
     */
    public void notifyUpdate(Integer familyId, String kind) {
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

    private void sendBatchNotification(Integer familyId, String kind) {
        String familyIdStr = familyId.toString();
        String countKey = "sync:family:" + familyIdStr + ":" + kind + ":count";
        String timeKey = "sync:family:" + familyIdStr + ":" + kind + ":last_sent";
        String msgId = java.util.UUID.randomUUID().toString();

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
                log.info("Sent batch update notification to device {}: msg_id={}, kind={}",
                        device.getSerialNumber(), msgId, kind);
            }

            // 상태 초기화
            redisService.deleteData(countKey);
            redisService.setDataWithExpiration(timeKey, String.valueOf(System.currentTimeMillis()),
                    24 * 60 * 60 * 1000L); // 24시간 유지

        } catch (Exception e) {
            log.error("Failed to send batch notification: {}", e.getMessage());
        }
    }

    /**
     * 주기적 확인 (10분마다 실행)
     * 이 스케줄러는 단순히 "확인"하는 트리거 역할입니다.
     * 실제 전송 조건은 (마지막 전송으로부터 1시간 경과) 여부입니다.
     */
    @Scheduled(fixedRate = 10 * 60 * 1000) // 10분 주기 체크
    public void checkPeriodicUpdates() {
        String activeFamilyKey = "sync:active_families";
        java.util.Set<Object> families = redisService.getSetMembers(activeFamilyKey);

        if (families == null || families.isEmpty())
            return;

        long now = System.currentTimeMillis();
        long oneHour = 60 * 60 * 1000L;

        for (Object famIdObj : families) {
            try {
                String familyIdStr = (String) famIdObj;
                Integer familyId = Integer.parseInt(familyIdStr);

                String activeKindsKey = "sync:family:" + familyIdStr + ":kinds";
                java.util.Set<Object> kinds = redisService.getSetMembers(activeKindsKey);

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
                            // 데이터가 있으면 전송 (내부에서 초기화됨)
                            sendBatchNotification(familyId, kind);
                        } else {
                            // 데이터가 0개면 전송하지 않고 시간만 초기화 (1시간 뒤에 다시 체크)
                            redisService.setDataWithExpiration(timeKey, String.valueOf(now), 24 * 60 * 60 * 1000L);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error checking periodic updates for family: {}", famIdObj, e);
            }
        }
    }
}
