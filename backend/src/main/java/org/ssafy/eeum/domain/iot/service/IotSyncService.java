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

    
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    
    public void notifyUpdate(Integer familyId, String kind) {
        eventPublisher.publishEvent(new org.ssafy.eeum.domain.iot.event.IotSyncEvent(familyId, kind));
    }

    
    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void handleSyncEvent(org.ssafy.eeum.domain.iot.event.IotSyncEvent event) {
        Integer familyId = event.getFamilyId();
        String kind = event.getKind();

        String familyIdStr = familyId.toString();
        String countKey = "sync:family:" + familyIdStr + ":" + kind + ":count";
        String activeFamilyKey = "sync:active_families";
        String activeKindsKey = "sync:family:" + familyIdStr + ":kinds";

        
        redisService.addToSet(activeFamilyKey, familyIdStr);
        redisService.addToSet(activeKindsKey, kind);

        
        redisService.increment(countKey);

        
        sendBatchNotification(familyId, kind);
    }

    private void sendBatchNotification(Integer familyId, String kind) {
        String familyIdStr = familyId.toString();
        String countKey = "sync:family:" + familyIdStr + ":" + kind + ":count";
        String timeKey = "sync:family:" + familyIdStr + ":" + kind + ":last_sent";
        String msgId = java.util.UUID.randomUUID().toString();

        
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

            
            redisService.deleteData(countKey);
            redisService.setDataWithExpiration(timeKey, String.valueOf(System.currentTimeMillis()),
                    24 * 60 * 60 * 1000L); 

        } catch (Exception e) {
            log.error("Failed to send batch notification: {}", e.getMessage());
        }
    }

    
    @Scheduled(fixedRate = 10 * 60 * 1000) 
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
                log.error("Error checking periodic updates for family: {}", famIdObj, e);
            }
        }
    }
}
