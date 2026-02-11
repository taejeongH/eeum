package org.ssafy.eeum.domain.iot.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.global.common.model.BaseEntity;

/**
 * IoT 기기로 전송된 알림 메시지 이력을 관리하는 엔티티 클래스입니다.
 * 음성 메시지, 이미지 전송 등 기기에 전달된 각종 알림의 읽음 여부를 추적합니다.
 * 
 * @summary 기기용 알림 이력 엔티티
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "iot_notifications")
public class IotNotification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, length = 100)
    private String serialNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Family family;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind; // medication, schedule, image, voice 등

    @Column(name = "message_id", nullable = false, length = 100, unique = true)
    private String messageId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Builder
    public IotNotification(String serialNumber, Family family, String kind, String messageId, String content) {
        this.serialNumber = serialNumber;
        this.family = family;
        this.kind = kind;
        this.messageId = messageId;
        this.content = content;
        this.isRead = false;
    }

    /**
     * 알림을 읽음 상태로 변경합니다.
     * 
     * @summary 알림 읽음 처리
     */
    public void markAsRead() {
        this.isRead = true;
    }
}
