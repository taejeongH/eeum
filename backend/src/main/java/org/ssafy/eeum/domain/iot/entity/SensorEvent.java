package org.ssafy.eeum.domain.iot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * IoT 기기의 각종 센서로부터 수집된 원시 이벤트 정보를 관리하는 엔티티 클래스입니다.
 * 움직임 감지, 상태 변화 등 기기로부터 수신된 상세 데이터를 저장합니다.
 * 
 * @summary 센서 감지 이벤트 엔티티
 */
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
    private String eventId;

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
    private String location;

    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private Boolean processed = false;

    /**
     * 시리얼 번호와 기기 고유 이벤트 ID를 조합하여 서버 측 이벤트 식별자를 생성합니다.
     * 
     * @summary 이벤트 식별자 생성
     * @param serialNumber  기기 시리얼 번호
     * @param deviceEventId 기기 발행 이벤트 식별자
     * @return 생성된 식별자 문자열
     */
    public static String generateEventId(String serialNumber, String deviceEventId) {
        return serialNumber + "_" + deviceEventId;
    }

    /**
     * 해당 이벤트의 후속 처리가 완료되었음을 표시합니다.
     * 
     * @summary 이벤트 처리 완료 표시
     */
    public void markAsProcessed() {
        this.processed = true;
    }
}
