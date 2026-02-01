package org.ssafy.eeum.domain.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ssafy.eeum.domain.album.entity.MediaAsset;
import org.ssafy.eeum.domain.album.repository.AlbumRepository;
import org.ssafy.eeum.domain.album.repository.MediaLogRepository;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.iot.dto.IotSyncDto;
import org.ssafy.eeum.domain.iot.entity.ActionType;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;
import org.ssafy.eeum.domain.message.entity.Message;
import org.ssafy.eeum.domain.message.repository.MessageRepository;
import org.ssafy.eeum.domain.voice.repository.VoiceLogRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.mqtt.MqttService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final FamilyRepository familyRepository;
    private final IotSyncDto.SyncItem dummy = null;

    public IotSyncDto getSyncData(String serialNumber, String kind) {
        IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        Integer familyId = device.getFamily().getId();
        Integer lastLogId = "image".equals(kind) ? device.getFamily().getLastMediaLogId()
                : device.getFamily().getLastVoiceLogId();

        List<Integer> deletedIds = new java.util.ArrayList<>();
        Map<Integer, Integer> addedMap = new HashMap<>();

        if ("image".equals(kind)) {
            List<org.ssafy.eeum.domain.album.entity.MediaLog> logs = mediaLogRepository
                    .findByGroupIdAndIdGreaterThan(familyId, lastLogId);
            if (logs.isEmpty())
                return IotSyncDto.builder().added(List.of()).deleted(List.of())
                        .lastLogId(lastLogId).build();

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
                        .build());
                addedMap.remove(asset.getId());
            }
            return IotSyncDto.builder()
                    .added(addedItems)
                    .deleted(deletedIds)
                    .lastLogId(maxLogId)
                    .build();

        } else if ("voice".equals(kind)) {
            List<org.ssafy.eeum.domain.voice.entity.VoiceLog> logs = voiceLogRepository
                    .findByGroupIdAndIdGreaterThan(familyId, lastLogId);
            if (logs.isEmpty())
                return IotSyncDto.builder().added(List.of()).deleted(List.of())
                        .lastLogId(lastLogId).build();

            int maxLogId = lastLogId;
            for (var log : logs) {
                maxLogId = Math.max(maxLogId, log.getId());
                if (log.getActionType() == org.ssafy.eeum.domain.iot.entity.ActionType.DELETE) {
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
                        .build());
            }

            return IotSyncDto.builder()
                    .added(addedItems)
                    .deleted(deletedIds)
                    .lastLogId(maxLogId)
                    .build();
        }

        return null;
    }

    /**
     * 특정 가족 그룹의 기기들에게 데이터 업데이트 알림을 전송합니다.
     * 
     * @param familyId  가족 그룹 ID
     * @param kind      업데이트 종류 (image, voice, text, schedule)
     * @param updateCnt 업데이트된 항목 수
     */
    public void notifyUpdate(Integer familyId, String kind, int updateCnt) {
        List<IotDevice> devices = iotDeviceRepository.findAllByFamilyId(familyId);

        if (devices.isEmpty()) {
            log.debug("No devices found for familyId: {}. Skipping sync notification.", familyId);
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("kind", kind);
        payload.put("kind", kind);
        // payload.put("update_cnt", updateCnt); // 요청에 의해 제거됨

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            for (IotDevice device : devices) {
                String topic = String.format("eeum/device/%s/update", device.getSerialNumber());
                mqttService.publish(topic, jsonPayload);
                log.info("Sent update notification to device {}: kind={}, count={}",
                        device.getSerialNumber(), kind, updateCnt);
            }
        } catch (Exception e) {
            log.error("Failed to serialize sync notification: {}", e.getMessage());
        }
    }
}
