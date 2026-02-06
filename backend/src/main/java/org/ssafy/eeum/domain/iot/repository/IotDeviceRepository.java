package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.iot.entity.IotDevice;

import java.util.List;
import java.util.Optional;

public interface IotDeviceRepository extends JpaRepository<IotDevice, Integer> {
    List<IotDevice> findAllByFamilyId(Integer familyId);

    Optional<IotDevice> findBySerialNumber(String serialNumber);

    boolean existsBySerialNumber(String serialNumber);
}
