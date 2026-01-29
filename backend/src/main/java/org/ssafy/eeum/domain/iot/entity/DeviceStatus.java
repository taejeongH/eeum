package org.ssafy.eeum.domain.iot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.family.entity.Family;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DeviceStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_device_id", nullable = false)
    private IotDevice masterDevice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slave_device_id", nullable = false)
    private IotDevice slaveDevice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Family family;

    @Column(name = "is_alive", nullable = false)
    private Boolean isAlive;

    @Column(name = "last_sync_at", nullable = false)
    private LocalDateTime lastSyncAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updateStatus(Boolean isAlive) {
        this.isAlive = isAlive;
        this.lastSyncAt = LocalDateTime.now();
    }
}
