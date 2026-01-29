package org.ssafy.eeum.domain.iot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SensorEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "event_id", unique = true, length = 200)
    private String eventId; // {serial_number}_{device_event_id} 형식

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private IotDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Family family;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType; // fall_detected, motion_detected 등

    @Column(name = "kind", length = 20)
    private String kind; // vision, sensor

    @Column(name = "location", length = 50)
    private String location; // LIVING_ROOM 등

    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData; // JSON 형태의 추가 데이터

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private Boolean processed = false; // FallEvent 생성 여부

    /**
     * 기기 시리얼 번호 + 기기 이벤트 ID로 고유 이벤트 ID 생성
     */
    public static String generateEventId(String serialNumber, String deviceEventId) {
        return serialNumber + "_" + deviceEventId;
    }

    public void markAsProcessed() {
        this.processed = true;
    }
}
