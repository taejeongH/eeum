package org.ssafy.eeum.domain.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.iot.entity.FallEvent;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.entity.SensorEvent;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;
import org.ssafy.eeum.domain.iot.repository.SensorEventRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorEventService {

    private final SensorEventRepository sensorEventRepository;
    private final IotDeviceRepository iotDeviceRepository;
    private final FamilyRepository familyRepository;
    private final FallEventService fallEventService;

    /**
     * MQTT 센서 이벤트 처리 (중복 방지 포함)
     * 
     * @param deviceEventId 기기에서 보낸 이벤트 ID (기기별로 고유)
     */
    @Transactional
    public SensorEvent handleSensorEvent(
            Integer groupId,
            String deviceEventId,
            String serialNumber,
            String kind,
            String eventType,
            String location,
            LocalDateTime startedAt,
            LocalDateTime detectedAt,
            String eventData) {

        // 1. 고유 이벤트 ID 생성 (serial_number + device_event_id)
        String eventId = SensorEvent.generateEventId(serialNumber, deviceEventId);

        // 2. 중복 체크
        if (sensorEventRepository.existsByEventId(eventId)) {
            log.warn("Duplicate event ignored: {}", eventId);
            return sensorEventRepository.findByEventId(eventId)
                    .orElseThrow();
        }

        // 3. 기기 조회
        IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.IOT_DEVICE_NOT_FOUND));

        // 4. 가족 조회
        Family family = familyRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        // 5. 센서 이벤트 저장
        SensorEvent sensorEvent = SensorEvent.builder()
                .eventId(eventId)
                .device(device)
                .family(family)
                .serialNumber(serialNumber)
                .eventType(eventType)
                .kind(kind)
                .location(location)
                .eventData(eventData)
                .detectedAt(detectedAt)
                .startedAt(startedAt)
                .processed(false)
                .build();

        sensorEventRepository.save(sensorEvent);
        log.info("Saved SensorEvent: eventId={}, type={}, serial={}", eventId, eventType, serialNumber);

        // 6. 낙상 이벤트인 경우 FallEvent 생성
        if ("fall_detected".equals(eventType)) {
            createFallEventFromSensor(sensorEvent);
        }

        return sensorEvent;
    }

    /**
     * 센서 이벤트 → FallEvent 변환
     */
    private void createFallEventFromSensor(SensorEvent sensorEvent) {
        FallEvent fallEvent = FallEvent.builder()
                .family(sensorEvent.getFamily())
                .severity(1) // 초기 심각도
                .statusType(FallEvent.StatusType.UNDER_REVIEW)
                .build();

        fallEventService.saveFallEvent(fallEvent);
        sensorEvent.markAsProcessed();

        log.info("Created FallEvent from SensorEvent: eventId={}, fallEventId={}",
                sensorEvent.getEventId(), fallEvent.getId());
    }

    /**
     * 이벤트 ID로 음성 응답 연동
     * 
     * @param deviceEventId 기기에서 보낸 이벤트 ID
     */
    @Transactional
    public void linkVoiceResponseToEvent(String serialNumber, String deviceEventId, String sttContent) {
        String eventId = SensorEvent.generateEventId(serialNumber, deviceEventId);

        SensorEvent sensorEvent = sensorEventRepository.findByEventId(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                        "센서 이벤트를 찾을 수 없습니다: " + eventId));

        // FallEvent 업데이트 로직 호출
        fallEventService.handleVoiceResponse(
                sensorEvent.getFamily().getId(),
                sttContent);

        log.info("Linked voice response to event: eventId={}, stt={}", eventId, sttContent);
    }
}
