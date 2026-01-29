package org.ssafy.eeum.domain.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;
import org.ssafy.eeum.global.infra.mqtt.MqttService;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        payload.put("update_cnt", updateCnt);

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
