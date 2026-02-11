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

/**
 * IoT 기기(센서 등)로부터 발생하는 다양한 이벤트를 수신하고 처리하는 서비스 클래스입니다.
 * 수신된 센서 데이터를 기록하고, 낙상 감지 시 관련 이벤트를 연동합니다.
 * 
 * @summary 센서 이벤트 처리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorEventService {

        private final SensorEventRepository sensorEventRepository;
        private final IotDeviceRepository iotDeviceRepository;
        private final FamilyRepository familyRepository;
        private final FallEventService fallEventService;

        /**
         * MQTT를 통해 수신된 센서 이벤트를 처리하고 저장합니다.
         * 기기별 고유 이벤트 ID를 생성하여 중복 처리를 방지하며, 낙상 감지 시 낙상 이벤트를 생성합니다.
         * 
         * @summary 센서 이벤트 수신 및 처리
         * @param groupId       가족 그룹 식별자
         * @param deviceEventId 기기에서 전송한 고유 이벤트 ID
         * @param serialNumber  기기 시리얼 번호
         * @param kind          센서 종류
         * @param eventType     이벤트 유형 (예: fall_detected)
         * @param location      발생 위치
         * @param startedAt     이벤트 시작 시점
         * @param detectedAt    이벤트 감지 시점
         * @param eventData     추가 센서 데이터 (JSON 등)
         * @return 저장된 센서 이벤트 엔티티
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

                String eventId = SensorEvent.generateEventId(serialNumber, deviceEventId);

                if (sensorEventRepository.existsByEventId(eventId)) {
                        return sensorEventRepository.findByEventId(eventId)
                                        .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR));
                }

                IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                                .orElseThrow(() -> new CustomException(ErrorCode.IOT_DEVICE_NOT_FOUND));

                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                SensorEvent sensorEvent = createAndSaveSensorEvent(eventId, device, family, serialNumber,
                                kind, eventType, location, startedAt, detectedAt, eventData);

                if ("fall_detected".equals(eventType)) {
                        processFallDetectionFromSensor(sensorEvent);
                }

                return sensorEvent;
        }

        private SensorEvent createAndSaveSensorEvent(String eventId, IotDevice device, Family family,
                        String serialNumber,
                        String kind, String eventType, String location, LocalDateTime startedAt,
                        LocalDateTime detectedAt, String eventData) {
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

                return sensorEventRepository.save(sensorEvent);
        }

        private void processFallDetectionFromSensor(SensorEvent sensorEvent) {
                FallEvent fallEvent = FallEvent.builder()
                                .family(sensorEvent.getFamily())
                                .severity(1)
                                .statusType(FallEvent.StatusType.UNDER_REVIEW)
                                .build();

                fallEventService.saveFallEvent(fallEvent);
                sensorEvent.markAsProcessed();
        }

        /**
         * 기기에서 수신한 음성 응답 텍스트를 특정 센서 이벤트와 매핑하여 처리합니다.
         * 
         * @summary 음성 응답과 센서 이벤트 매핑
         * @param serialNumber    기기 시리얼 번호
         * @param deviceEventId   기기 전송 이벤트 ID
         * @param voiceTranscript 음성 인식(STT) 결과 텍스트
         */
        @Transactional
        public void linkVoiceResponseToEvent(String serialNumber, String deviceEventId, String voiceTranscript) {
                String eventId = SensorEvent.generateEventId(serialNumber, deviceEventId);

                SensorEvent sensorEvent = sensorEventRepository.findByEventId(eventId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "센서 이벤트를 찾을 수 없습니다: " + eventId));

                fallEventService.handleVoiceResponse(
                                sensorEvent.getFamily().getId(),
                                voiceTranscript);
        }
}
