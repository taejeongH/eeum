package org.ssafy.eeum.global.infra.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttService {

    private final MessageChannel mqttOutboundChannel;

    public void publish(String topic, String payload) {
        try {
            Message<String> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .build();

            mqttOutboundChannel.send(message);
            log.info("MQTT Publish Success - Topic: {}, Payload: {}", topic, payload);
        } catch (Exception e) {
            log.error("MQTT Publish Failed - Topic: {}, Error: {}", topic, e.getMessage());
        }
    }
}
