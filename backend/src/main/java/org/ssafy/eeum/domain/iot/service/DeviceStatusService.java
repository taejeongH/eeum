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

        /**
         * Master-Slave 상태 업데이트 (Upsert)
         */
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

                // 기존 상태 조회 또는 신규 생성
                DeviceStatus status = deviceStatusRepository
                                .findByMasterDeviceIdAndSlaveDeviceId(master.getId(), slave.getId())
                                .orElseGet(() -> DeviceStatus.builder()
                                                .masterDevice(master)
                                                .slaveDevice(slave)
                                                .family(family)
                                                .isAlive(isAlive)
                                                .lastSyncAt(LocalDateTime.now())
                                                .build());

                // 상태 변경 감지 (Offline → Online 또는 그 반대)
                if (status.getIsAlive() != null && !status.getIsAlive().equals(isAlive)) {
                        log.warn("Device status changed: Master={}, Slave={}, {} → {}",
                                        masterSerial, slaveSerial, status.getIsAlive(), isAlive);

                        // Offline 알림
                        if (!isAlive) {
                                sendOfflineAlert(family, slave);
                        }
                }

                status.updateStatus(isAlive);
                deviceStatusRepository.save(status);

                log.debug("Updated device status: Master={}, Slave={}, alive={}",
                                masterSerial, slaveSerial, isAlive);
        }

        /**
         * Offline 알림 발송 (향후 구현)
         */
        private void sendOfflineAlert(Family family, IotDevice device) {
                log.info("Device went offline: Family={}, Device={}",
                                family.getId(), device.getSerialNumber());
                // TODO: FCM Push 알림 또는 MQTT 알림
        }

        /**
         * 그룹의 모든 기기 상태 조회
         */
        @Transactional(readOnly = true)
        public List<DeviceStatus> getGroupDeviceStatus(Integer groupId) {
                return deviceStatusRepository.findByFamilyId(groupId);
        }

        /**
         * Offline 상태인 기기 목록 조회
         */
        @Transactional(readOnly = true)
        public List<DeviceStatus> getOfflineDevices() {
                return deviceStatusRepository.findByIsAliveFalse();
        }

        /**
         * 디바이스를 오프라인으로 표시 (LWT 메시지 처리용)
         * Master 디바이스가 오프라인이 되면 해당 디바이스와 연결된 모든 Slave 상태를 offline으로 변경
         */
        @Transactional
        public void markDeviceOffline(Integer groupId, String serialNumber) {
                IotDevice device = iotDeviceRepository.findBySerialNumber(serialNumber)
                                .orElseThrow(() -> new CustomException(ErrorCode.IOT_DEVICE_NOT_FOUND,
                                                "기기를 찾을 수 없습니다: " + serialNumber));

                // Master로 등록된 모든 연결 상태를 offline으로 변경
                List<DeviceStatus> statuses = deviceStatusRepository.findByMasterDeviceId(device.getId());
                for (DeviceStatus status : statuses) {
                        if (status.getIsAlive() != null && status.getIsAlive()) {
                                status.updateStatus(false);
                                deviceStatusRepository.save(status);
                                log.info("Marked device offline: Master={}, Slave={}",
                                                serialNumber, status.getSlaveDevice().getSerialNumber());
                        }
                }

                log.info("Device marked as offline: Family={}, Serial={}", groupId, serialNumber);
        }
}
