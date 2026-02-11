package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.iot.entity.IotDevice;

import java.util.List;
import java.util.Optional;

/**
 * IoT 기기 데이터에 접근하기 위한 레포지토리 인터페이스입니다.
 * 시리얼 번호를 통한 기기 조회 및 가족별 기기 목록 조회 기능을 제공합니다.
 * 
 * @summary IoT 기기 레포지토리
 */
public interface IotDeviceRepository extends JpaRepository<IotDevice, Integer> {

    /**
     * 특정 가족 그룹에 속한 모든 IoT 기기 목록을 조회합니다.
     * 
     * @summary 가족별 기기 목록 조회
     * @param familyId 가족 그룹 식별자
     * @return 기기 리스트
     */
    List<IotDevice> findAllByFamilyId(Integer familyId);

    /**
     * 시리얼 번호를 기준으로 기기를 조회합니다.
     * 
     * @summary 시리얼 번호 기반 기기 조회
     * @param serialNumber 기기 시리얼 번호
     * @return 기기 정보 (Optional)
     */
    Optional<IotDevice> findBySerialNumber(String serialNumber);

    /**
     * 해당 시리얼 번호를 가진 기기가 이미 존재하는지 확인합니다.
     * 
     * @summary 기기 존재 여부 확인
     * @param serialNumber 기기 시리얼 번호
     * @return 존재 여부
     */
    boolean existsBySerialNumber(String serialNumber);
}
