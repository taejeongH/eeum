package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.iot.entity.DeviceStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceStatusRepository extends JpaRepository<DeviceStatus, Integer> {

    Optional<DeviceStatus> findByMasterDeviceIdAndSlaveDeviceId(
            Integer masterDeviceId, Integer slaveDeviceId);

    List<DeviceStatus> findByMasterDeviceId(Integer masterDeviceId);

    List<DeviceStatus> findByFamilyId(Integer familyId);

    List<DeviceStatus> findByIsAliveFalse();

    List<DeviceStatus> findByLastSyncAtBefore(LocalDateTime threshold);
}
