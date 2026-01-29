package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.iot.entity.DeviceStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceStatusRepository extends JpaRepository<DeviceStatus, Integer> {

    // Master-Slave 조합으로 조회
    Optional<DeviceStatus> findByMasterDeviceIdAndSlaveDeviceId(
            Integer masterDeviceId, Integer slaveDeviceId);

    // Master 기기의 모든 Slave 상태 조회
    List<DeviceStatus> findByMasterDeviceId(Integer masterDeviceId);

    // 그룹의 모든 기기 상태 조회
    List<DeviceStatus> findByFamilyId(Integer familyId);

    // Offline 상태인 기기 조회
    List<DeviceStatus> findByIsAliveFalse();

    // 특정 시간 이후 동기화되지 않은 기기 조회
    List<DeviceStatus> findByLastSyncAtBefore(LocalDateTime threshold);
}
