package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.iot.entity.DeviceStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 기기 연결 상태(DeviceStatus) 데이터에 접근하기 위한 레포지토리 인터페이스입니다.
 * 기기 간의 헬스체크 결과 및 활성 상태 조회를 지원합니다.
 * 
 * @summary 기기 연결 상태 레포지토리
 */
public interface DeviceStatusRepository extends JpaRepository<DeviceStatus, Integer> {

    /**
     * 제어기(Master)와 하위 기기(Slave)의 ID를 기반으로 연결 상태를 조회합니다.
     * 
     * @summary 기기 쌍 기반 상태 조회
     * @param masterDeviceId 제어기 ID
     * @param slaveDeviceId  하위 기기 ID
     * @return 기기 상태 정보 (Optional)
     */
    Optional<DeviceStatus> findByMasterDeviceIdAndSlaveDeviceId(
            Integer masterDeviceId, Integer slaveDeviceId);

    /**
     * 특정 제어기가 관리하는 모든 하위 기기들의 연결 상태 목록을 조회합니다.
     * 
     * @summary 제어기별 기기 상태 조회
     * @param masterDeviceId 제어기 ID
     * @return 기기 상태 리스트
     */
    List<DeviceStatus> findByMasterDeviceId(Integer masterDeviceId);

    /**
     * 특정 가족 그룹에 속한 모든 기기 쌍의 연결 상태 목록을 조회합니다.
     * 
     * @summary 가족별 기기 상태 조회
     * @param familyId 가족 그룹 식별자
     * @return 기기 상태 리스트
     */
    List<DeviceStatus> findByFamilyId(Integer familyId);

    /**
     * 현재 연결이 끊어져 있는(isAlive=false) 모든 기기 상태 목록을 조회합니다.
     * 
     * @summary 오프라인 기기 목록 조회
     * @return 오프라인 기기 상태 리스트
     */
    List<DeviceStatus> findByIsAliveFalse();

    /**
     * 특정 시점 이전에 동기화된 기기 상태 목록을 조회합니다. (임계치 기반 오프라인 판단용)
     * 
     * @summary 최종 동기화 시점 기준 조회
     * @param threshold 기준 시간
     * @return 기기 상태 리스트
     */
    List<DeviceStatus> findByLastSyncAtBefore(LocalDateTime threshold);
}
