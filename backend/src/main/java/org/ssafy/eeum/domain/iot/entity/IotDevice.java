package org.ssafy.eeum.domain.iot.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.global.common.model.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "iot_devices")
@SQLDelete(sql = "UPDATE iot_devices SET deleted_at = NOW() WHERE id = ?")
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
public class IotDevice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

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
    private String deviceType; // RASPBERRY_PI, JETSON_ORIN_NANO 등

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
