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

/**
 * 가족 내 설치된 IoT 기기들의 실시간 연결 상태 및 네트워크 가동 현황을 관리하는 서비스 클래스입니다.
 * Master(제어기)와 Slave(센서/모듈) 기기 간의 헬스체크 결과를 바탕으로 시스템의 건전성을 모니터링합니다.
 * 
 * @summary IoT 기기 연결 상태 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStatusService {

        private final DeviceStatusRepository deviceStatusRepository;
        private final IotDeviceRepository iotDeviceRepository;
        private final FamilyRepository familyRepository;

        /**
         * 특정 기기 쌍(Master-Slave)의 연결 상태를 업데이트합니다.
         * 기존 상태가 존재하면 갱신하고, 없으면 새로 생성합니다.
         * 
         * @summary 기기 연결 상태 업데이트
         * @param groupId      가족 그룹 식별자
         * @param masterSerial 제어기 시리얼 번호
         * @param slaveSerial  하위 기기 시리얼 번호
         * @param isAlive      연결 성공 여부
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

                DeviceStatus status = deviceStatusRepository
                                .findByMasterDeviceIdAndSlaveDeviceId(master.getId(), slave.getId())
                                .orElseGet(() -> DeviceStatus.builder()
                                                .masterDevice(master)
                                                .slaveDevice(slave)
                                                .family(family)
                                                .isAlive(isAlive)
                                                .lastSyncAt(LocalDateTime.now())
                                                .build());

                status.updateStatus(isAlive);
                deviceStatusRepository.save(status);
        }

        /**
         * 특정 가족 그룹에 등록된 모든 기기들의 현재 연결 상태 목록을 조회합니다.
         * 
         * @summary 가족별 기기 상태 목록 조회
         * @param groupId 가족 그룹 식별자
         * @return 기기 상태 엔티티 리스트
         */
        @Transactional(readOnly = true)
        public List<DeviceStatus> getGroupDeviceStatus(Integer groupId) {
                return deviceStatusRepository.findByFamilyId(groupId);
        }

        /**
         * 현재 연결이 끊긴(Offline) 모든 기기 상태 목록을 조회합니다.
         * 
         * @summary 연결 끊긴 기기 목록 조회
         * @return 오프라인 기기 상태 리스트
         */
        @Transactional(readOnly = true)
        public List<DeviceStatus> getOfflineDevices() {
                return deviceStatusRepository.findByIsAliveFalse();
        }

        /**
         * 특정 기기가 연결 해제되었을 때, 해당 기기가 Master 역할을 수행하는 모든 상태 정보를 오프라인으로 전환합니다.
         * 
         * @summary 기기 오프라인 강제 마킹
         * @param groupId      가족 그룹 식별자
         * @param serialNumber 연결 해제된 기기 시리얼 번호
         */
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
