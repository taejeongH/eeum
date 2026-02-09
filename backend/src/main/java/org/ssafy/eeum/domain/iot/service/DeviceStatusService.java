package org.ssafy.eeum.domain.iot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.iot.entity.DeviceStatus;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.repository.DeviceStatusRepository;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStatusService {

        private final DeviceStatusRepository deviceStatusRepository;
        private final IotDeviceRepository iotDeviceRepository;
        private final FamilyRepository familyRepository;

        @Transactional
        public void updateDeviceStatus(
                        Integer groupId,
                        String masterSerial,
                        String slaveSerial,
                        Boolean isAlive) {

                IotDevice master = iotDeviceRepository.findBySerialNumber(masterSerial)
                                .orElseThrow(() -> new CustomException(ErrorCode.IOT_DEVICE_NOT_FOUND,
                                                "Master 기기를 찾을 수 없습니다: " + masterSerial));

                IotDevice slave = iotDeviceRepository.findBySerialNumber(slaveSerial)
                                .orElseThrow(() -> new CustomException(ErrorCode.IOT_DEVICE_NOT_FOUND,
                                                "Slave 기기를 찾을 수 없습니다: " + slaveSerial));

                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

                
                DeviceStatus status = deviceStatusRepository
                                .findByMasterDeviceIdAndSlaveDeviceId(master.getId(), slave.getId())
                                .orElseGet(() -> DeviceStatus.builder()
                                                .masterDevice(master)
                                                .slaveDevice(slave)
                                                .family(family)
                                                .isAlive(isAlive)
                                                .lastSyncAt(LocalDateTime.now())
                                                .build());

                
                if (status.getIsAlive() != null && !status.getIsAlive().equals(isAlive)) {

                }

                status.updateStatus(isAlive);
                deviceStatusRepository.save(status);
        }

        @Transactional(readOnly = true)
        public List<DeviceStatus> getGroupDeviceStatus(Integer groupId) {
                return deviceStatusRepository.findByFamilyId(groupId);
        }

        @Transactional(readOnly = true)
        public List<DeviceStatus> getOfflineDevices() {
                return deviceStatusRepository.findByIsAliveFalse();
        }

        @Transactional
        public void markDeviceOffline(Integer groupId, String serialNumber) {
                IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                                .orElseThrow(() -> new CustomException(ErrorCode.IOT_DEVICE_NOT_FOUND,
                                                "기기를 찾을 수 없습니다: " + serialNumber));

                List<DeviceStatus> statuses = deviceStatusRepository.findByMasterDeviceId(device.getId());
                for (DeviceStatus status : statuses) {
                        if (status.getIsAlive() != null && status.getIsAlive()) {
                                status.updateStatus(false);
                                deviceStatusRepository.save(status);
                        }
                }
        }
}
