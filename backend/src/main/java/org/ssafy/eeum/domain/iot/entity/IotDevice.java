package org.ssafy.eeum.domain.iot.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * IoT 기기 정보를 관리하는 엔티티 클래스입니다.
 * 기기 시리얼 번호, 이름, 설치 위치 및 활성화 상태 등을 관리합니다.
 * 소프트 딜리트(SQLDelete) 기능을 지원합니다.
 * 
 * @summary IoT 기기 엔티티
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "iot_devices")
@SQLDelete(sql = "UPDATE iot_devices SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class IotDevice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Family family;

    @Column(name = "serial_number", nullable = false, unique = true, length = 100)
    private String serialNumber;

    @Column(name = "device_name", length = 50)
    private String deviceName;

    @Column(name = "location_type", length = 20)
    private String locationType;

    @Column(name = "device_type", length = 30)
    private String deviceType;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Builder
    public IotDevice(Family family, String serialNumber, String deviceName, String locationType, String deviceType) {
        this.family = family;
        this.serialNumber = serialNumber;
        this.deviceName = deviceName;
        this.locationType = locationType;
        this.deviceType = deviceType;
        this.isActive = true;
    }

    public void updateInfo(String deviceName, String locationType) {
        if (deviceName != null)
            this.deviceName = deviceName;
        if (locationType != null)
            this.locationType = locationType;
    }

    public void updatePairingInfo(Family family, String deviceType) {
        this.family = family;
        if (deviceType != null) {
            this.deviceType = deviceType;
        }
    }
}
