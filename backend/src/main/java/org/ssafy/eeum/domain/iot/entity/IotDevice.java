package org.ssafy.eeum.domain.iot.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "iot_devices")
@SQLDelete(sql = "UPDATE iot_devices SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class IotDevice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "group_id", nullable = false)
    private Integer groupId;

    @Column(name = "serial_number", nullable = false, unique = true, length = 100)
    private String serialNumber;

    @Column(name = "device_name", length = 50)
    private String deviceName;

    @Column(name = "location_type", length = 20)
    private String locationType;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Builder
    public IotDevice(Integer groupId, String serialNumber, String deviceName, String locationType) {
        this.groupId = groupId;
        this.serialNumber = serialNumber;
        this.deviceName = deviceName;
        this.locationType = locationType;
        this.isActive = true;
    }

    public void updateInfo(String deviceName, String locationType) {
        if (deviceName != null)
            this.deviceName = deviceName;
        if (locationType != null)
            this.locationType = locationType;
    }
}
