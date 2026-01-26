package org.ssafy.eeum.global.infra.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.ssafy.eeum.domain.iot.dto.IotDeviceMqttDTO;
import org.ssafy.eeum.domain.iot.service.FallEventService;
import org.ssafy.eeum.domain.iot.service.IotDeviceService;
import org.ssafy.eeum.domain.iot.dto.IotDeviceResponseDTO;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttService {

    private final MessageChannel mqttOutboundChannel;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final FallEventService fallEventService;
    private final IotDeviceService iotDeviceService;

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
            if ("eeum/sensor/data".equals(topic)) {
                handleSensorData(payload);
            } else if ("eeum/ai/sentiment".equals(topic)) {
                handleSentimentAnalysis(payload);
            } else if ("eeum/fall/response".equals(topic)) {
                handleFallResponse(payload);
            } else if ("eeum/device/init".equals(topic)) {
                handleDeviceInit(payload);
            }
        } catch (Exception e) {
            log.error("Error handling MQTT message for topic {}: {}", topic, e.getMessage());
        }
    }

    private void handleDeviceInit(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);

            if (!node.has("serial_number")) {
                log.error("Invalid Device Init Payload: Missing serial_number");
                return;
            }

            String serialNumber = node.path("serial_number").asText();
            List<IotDeviceMqttDTO> devices = iotDeviceService.getDevicesBySerialNumber(serialNumber);

            if (devices.isEmpty()) {
                log.warn("No devices found for serialNumber: {}", serialNumber);
                return;
            }

            Integer groupId = devices.get(0).getGroupId();
            String responsePayload = objectMapper.writeValueAsString(devices);

            sendToIot(groupId, "device-list", responsePayload);
            log.info("Sent Device List via MQTT: Group={}, Count={}", groupId, devices.size());

        } catch (Exception e) {
            log.error("Failed to handle Device Init: {}", e.getMessage());
        }
    }

    private void handleFallResponse(String payload) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);

            if (!node.has("group_id") || !node.has("stt_content")) {
                log.error("Invalid Fall Response Payload: Missing group_id or stt_content");
                return;
            }

            Integer groupId = node.path("group_id").asInt();
            String sttContent = node.path("stt_content").asText();

            fallEventService.handleVoiceResponse(groupId, sttContent);
            log.info("Processed Fall Response via MQTT: Group={}, Content={}", groupId, sttContent);
        } catch (Exception e) {
            log.error("Failed to handle Fall Response: {}", e.getMessage());
        }
    }

    private void handleSensorData(String payload) {
        // TODO: JSON 파싱 및 DB 저장
        log.info("Processing Sensor Data: {}", payload);
    }

    private void handleSentimentAnalysis(String payload) {
        // TODO: 텍스트 긍부정 판단 로직
        log.info("Processing Sentiment Analysis: {}", payload);
    }
}
