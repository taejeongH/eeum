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
                    .orElseThrow();
        }

        
        IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.IOT_DEVICE_NOT_FOUND));

        
        Family family = familyRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        
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
        
        if ("fall_detected".equals(eventType)) {
            createFallEventFromSensor(sensorEvent);
        }

        return sensorEvent;
    }

    
    private void createFallEventFromSensor(SensorEvent sensorEvent) {
        FallEvent fallEvent = FallEvent.builder()
                .family(sensorEvent.getFamily())
                .severity(1) 
                .statusType(FallEvent.StatusType.UNDER_REVIEW)
                .build();

        fallEventService.saveFallEvent(fallEvent);
        sensorEvent.markAsProcessed();


    }

    
    @Transactional
    public void linkVoiceResponseToEvent(String serialNumber, String deviceEventId, String sttContent) {
        String eventId = SensorEvent.generateEventId(serialNumber, deviceEventId);

        SensorEvent sensorEvent = sensorEventRepository.findByEventId(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                        "센서 이벤트를 찾을 수 없습니다: " + eventId));

        
        fallEventService.handleVoiceResponse(
                sensorEvent.getFamily().getId(),
                sttContent);


    }
}
