package org.ssafy.eeum.global.infra.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;

import org.ssafy.eeum.domain.family.entity.Family;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.ssafy.eeum.domain.iot.service.SensorEventService;
import org.ssafy.eeum.domain.iot.service.DeviceStatusService;
import org.ssafy.eeum.domain.iot.service.FallEventService;
import org.ssafy.eeum.domain.iot.event.IotDeviceEvent;
import org.ssafy.eeum.global.auth.jwt.JwtProvider;
import org.springframework.context.event.EventListener;

import org.ssafy.eeum.global.auth.model.DeviceDetails;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttService {

    private final MessageChannel mqttOutboundChannel;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final SensorEventService sensorEventService;
    private final DeviceStatusService deviceStatusService;
    private final FallEventService fallEventService;

    private final JwtProvider jwtProvider;
    private final FamilyRepository familyRepository;

    public void publish(String topic, String payload) {
        try {
            Message<String> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1)
                    .build();

            mqttOutboundChannel.send(message);
            log.info("MQTT Publish Success - Topic: {}, Payload: {}", topic, payload);
        } catch (Exception e) {
            log.error("MQTT Publish Failed - Topic: {}, Error: {}", topic, e.getMessage());
        }
    }

    public void sendToIot(Integer groupId, String category, String jsonPayload) {
        // 사용 형식 : eeum/group/{groupId}/{category}
        String topic = String.format("eeum/group/%d/%s", groupId, category);
        publish(topic, jsonPayload);
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) {
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        Object payloadObj = message.getPayload();
        String payload;

        if (payloadObj instanceof byte[]) {
            payload = new String((byte[]) payloadObj);
        } else {
            payload = payloadObj.toString();
        }

        log.info("MQTT Message Received - Topic: {}, Payload: {}", topic, payload);

        try {
            if ("eeum/sync".equals(topic)) {
                handleSync(payload);
            } else if ("eeum/response".equals(topic)) {
                handleResponse(payload);
            } else if ("eeum/event".equals(topic)) {
                handleEvent(payload);
            } else if ("eeum/update".equals(topic)) {
                handleUpdate(payload);
            } else if ("eeum/status".equals(topic)) {
                handleStatus(payload);
            } else if ("eeum/responsenull".equals(topic)) {
                handleResponseNull(payload);
            }
        } catch (Exception e) {
            log.error("Error handling MQTT message for topic {}: {}", topic, e.getMessage());
        }
    }

    private void handleSync(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String token = getTokenFromNode(node);
            Integer groupId = validateTokenAndGetGroupId(token);

            String masterSerialNumber = node.path("serial_number").asText();
            JsonNode linkArray = node.path("link");

            if (linkArray.isArray()) {
                for (JsonNode linkNode : linkArray) {
                    String slaveSerial = linkNode.path("id").asText();
                    boolean alive = linkNode.path("alive").asBoolean();

                    // DB에 상태 저장
                    deviceStatusService.updateDeviceStatus(
                            groupId, masterSerialNumber, slaveSerial, alive);
                }
            }
            log.info("Handled Status Sync for Family: {}", groupId);
        } catch (Exception e) {
            log.warn("Failed to handle sync: {}", e.getMessage());
        }
    }

    private void handleResponse(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String token = getTokenFromNode(node);
            Integer groupId = validateTokenAndGetGroupId(token);

            processResponse(node, groupId, payload);
        } catch (Exception e) {
            log.warn("Failed to handle response: {}", e.getMessage());
        }
    }

    private void handleResponseNull(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String sttContent = node.path("stt_content").asText();
            int familyId = node.path("family_id").asInt(9); // Default to 9 for testing

            log.info("Handling ResponseNull (LLM Test Mode) - stt_content: {}, familyId: {}", sttContent, familyId);
            fallEventService.testSentimentAnalysis(familyId, sttContent);
        } catch (Exception e) {
            log.warn("Failed to handle responseNull: {}", e.getMessage());
        }
    }

    private void processResponse(JsonNode node, Integer groupId, String payload) {
        try {
            String msgId = node.path("msg_id").asText();
            String serialNumber = node.path("serial_number").asText();
            String sttContent = node.path("stt_content").asText();
            double detectedAtTimestamp = node.path("detected_at").asDouble();

            LocalDateTime detectedAt = convertTimestamp(detectedAtTimestamp);

            log.info("Processing Voice Response Core: msg_id={}, serial_number={}, stt_content={}, groupId={}",
                    msgId, serialNumber, sttContent, groupId);

            // STT 내용이 비어있으면 즉시 비상 처리
            if (sttContent == null || sttContent.trim().isEmpty()) {
                fallEventService.handleEmptyVoiceResponse(groupId);
                log.warn("Empty STT Response - Auto EMERGENCY: MsgId={}, Family={}, SN={}",
                        msgId, groupId, serialNumber);
                return;
            }

            // 직접 FallEventService 호출 (가족 그룹 내 최신 낙상 이벤트 처리)
            fallEventService.handleVoiceResponse(groupId, sttContent);

            log.info("Successfully Processed Voice Response Core: MsgId={}, Family={}, SN={}, DetectedAt={}",
                    msgId, groupId, serialNumber, detectedAt);
        } catch (Exception e) {
            log.warn("Core response processing failed: {}", e.getMessage());
        }
    }

    private void handleEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String token = getTokenFromNode(node);
            Integer groupId = validateTokenAndGetGroupId(token);

            String msgId = node.path("msg_id").asText();
            String kind = node.path("kind").asText();
            String serialNumber = node.path("serial_number").asText();
            String eventType = node.path("event").asText();
            double startedAtTimestamp = node.path("started_at").asDouble();
            double detectedAtTimestamp = node.path("detected_at").asDouble();

            LocalDateTime startedAt = convertTimestamp(startedAtTimestamp);
            LocalDateTime detectedAt = convertTimestamp(detectedAtTimestamp);

            // 기존 로직과의 호환성을 위해 location 필드 유지 (선택적)
            String location = node.path("location").asText();

            // 센서 이벤트 저장 (중복 방지 포함)
            // Note: 기존에는 "id" 필드를 deviceEventId로 사용했으나, 새 명세에서는 msg_id 사용
            sensorEventService.handleSensorEvent(
                    groupId, msgId, serialNumber, kind, eventType,
                    location, startedAt, detectedAt, payload);

            log.info("Handled Sensor Event: MsgId={}, Kind={}, Event={}, SN={}, DetectedAt={}",
                    msgId, kind, eventType, serialNumber, detectedAt);
        } catch (Exception e) {
            log.warn("Failed to handle event: {}", e.getMessage());
        }
    }

    private LocalDateTime convertTimestamp(double timestamp) {
        return Instant.ofEpochMilli((long) (timestamp * 1000))
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private void handleStatus(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String token = getTokenFromNode(node);
            Integer groupId = validateTokenAndGetGroupId(token);

            String msgId = node.path("msg_id").asText();
            String serialNumber = node.path("serial_number").asText();
            String status = node.path("status").asText();
            double detectedAtTimestamp = node.path("detected_at").asDouble();

            LocalDateTime detectedAt = convertTimestamp(detectedAtTimestamp);

            if ("online".equals(status)) {
                JsonNode linkArray = node.path("link");
                if (linkArray.isArray()) {
                    for (JsonNode linkNode : linkArray) {
                        String slaveSerial = linkNode.path("id").asText();
                        boolean alive = linkNode.path("alive").asBoolean();

                        // DB에 상태 저장
                        deviceStatusService.updateDeviceStatus(
                                groupId, serialNumber, slaveSerial, alive);
                    }
                }
                log.info("Handled Device Status (Online): MsgId={}, Family={}, SN={}, DetectedAt={}",
                        msgId, groupId, serialNumber, detectedAt);
            } else if ("offline".equals(status)) {
                deviceStatusService.markDeviceOffline(groupId, serialNumber);
                log.info("Handled Device Status (Offline): MsgId={}, Family={}, SN={}, DetectedAt={}",
                        msgId, groupId, serialNumber, detectedAt);
            }
        } catch (Exception e) {
            log.warn("Failed to handle status: {}", e.getMessage());
        }
    }

    /**
     * Server -> IoT 업데이트 알림 전송
     * 
     * @param deviceId  디바이스 시리얼 번호
     * @param kind      업데이트 종류 (image/voice/text/schedule)
     * @param updateCnt 업데이트 카운트
     */
    public void sendDeviceUpdateNotification(String deviceId, String kind, Integer updateCnt) {
        try {
            String topic = String.format("eeum/device/%s/update", deviceId);

            String msgId = java.util.UUID.randomUUID().toString();
            double sentAt = System.currentTimeMillis() / 1000.0;

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("msg_id", msgId);
            payload.put("kind", kind);
            payload.put("update_cnt", updateCnt);
            payload.put("sent_at", sentAt);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            publish(topic, jsonPayload);

            log.info("Sent Device Update Notification: MsgId={}, DeviceId={}, Kind={}, Count={}",
                    msgId, deviceId, kind, updateCnt);
        } catch (Exception e) {
            log.error("Failed to send Device Update Notification: {}", e.getMessage());
        }
    }

    /**
     * IoT 기기로 알람 전송 (복약, 일정 등)
     * Topic: eeum/device/{device_id}/alarm
     */
    public void sendAlarm(String serialNumber, String kind, String content, java.util.Map<String, Object> data) {
        try {
            String topic = String.format("eeum/device/%s/alarm", serialNumber);
            String msgId = java.util.UUID.randomUUID().toString();
            double sentAt = System.currentTimeMillis() / 1000.0;

            org.ssafy.eeum.domain.iot.dto.MqttAlarmMessageDTO message = org.ssafy.eeum.domain.iot.dto.MqttAlarmMessageDTO
                    .builder()
                    .msgId(msgId)
                    .kind(kind)
                    .content(content)
                    .data(data)
                    .sentAt(sentAt)
                    .build();

            String jsonPayload = objectMapper.writeValueAsString(message);
            publish(topic, jsonPayload);

            log.info("Sent Alarm: SN={}, Kind={}, Content={}", serialNumber, kind, content);
        } catch (Exception e) {
            log.error("Failed to send alarm: {}", e.getMessage());
        }
    }

    @org.springframework.transaction.annotation.Transactional
    protected void handleUpdate(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String token = getTokenFromNode(node);
            DeviceDetails deviceDetails = validateTokenAndGetDeviceDetails(token);

            String kind = node.path("kind").asText();
            int logId = node.path("log_id").asInt(0); // Default 0 if missing

            Family family = familyRepository.findById(deviceDetails.getGroupId())
                    .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

            if ("image".equals(kind)) {
                if (logId > family.getLastMediaLogId()) {
                    family.updateLastMediaLogId(logId);
                }
            } else if ("voice".equals(kind)) {
                if (logId > family.getLastVoiceLogId()) {
                    family.updateLastVoiceLogId(logId);
                }
            }

            log.info("Handled Update Acknowledgement from IoT: Serial={}, Kind={}, LogId={}",
                    deviceDetails.getSerialNumber(), kind, logId);
        } catch (Exception e) {
            log.warn("Failed to handle IoT update acknowledgement: {}", e.getMessage());
        }
    }

    @EventListener
    public void handleDeviceEvent(IotDeviceEvent event) {
        try {
            String topic = String.format("eeum/device/%s/sync", event.getSerialNumber());
            String type = event.getType();

            Map<String, String> payload = new HashMap<>();
            payload.put("type", type.equals("UPDATE") ? "LOCATION_UPDATE" : "DEVICE_DELETE");
            if (event.getLocation() != null) {
                payload.put("location", event.getLocation());
            }

            String jsonPayload = objectMapper.writeValueAsString(payload);
            publish(topic, jsonPayload);
            log.info("Sent Device Sync Event via MQTT: Serial={}, Type={}", event.getSerialNumber(), type);
        } catch (Exception e) {
            log.error("Failed to send Device Sync Event via MQTT: {}", e.getMessage());
        }
    }

    private String getTokenFromNode(JsonNode node) {
        if (node.has("token"))
            return node.path("token").asText();
        if (node.has("toekn"))
            return node.path("toekn").asText();
        throw new IllegalArgumentException("Token is missing");
    }

    private Integer validateTokenAndGetGroupId(String token) {
        return validateTokenAndGetDeviceDetails(token).getGroupId();
    }

    private DeviceDetails validateTokenAndGetDeviceDetails(String token) {
        if (token == null)
            throw new IllegalArgumentException("Token is null");
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        if (!jwtProvider.validateToken(jwt)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        Authentication auth = jwtProvider.getAuthentication(jwt);
        Object principal = auth.getPrincipal();
        if (principal instanceof DeviceDetails) {
            return (DeviceDetails) principal;
        }
        throw new IllegalArgumentException("Invalid token type for IoT");
    }
}
