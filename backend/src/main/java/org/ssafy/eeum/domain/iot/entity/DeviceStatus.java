package org.ssafy.eeum.domain.iot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.family.entity.Family;

import java.time.LocalDateTime;

/**
 * 프로젝트 내 IoT 기기들 간의 실시간 연결 상태 정보를 관리하는 엔티티 클래스입니다.
 * Master(제어기)와 Slave(센서) 간의 헬스체크 이력을 저장합니다.
 * 
 * @summary 기기 연결 상태 엔티티
 */
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

    /**
     * 기기의 활성 상태 및 최근 동기화 시간을 업데이트합니다.
     * 
     * @summary 기기 상태 및 시간 갱신
     * @param isAlive 연결 성공 여부
     */
    public void updateStatus(Boolean isAlive) {
        this.isAlive = isAlive;
        this.lastSyncAt = LocalDateTime.now();
    }
}
