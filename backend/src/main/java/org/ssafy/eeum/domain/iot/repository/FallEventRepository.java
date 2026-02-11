package org.ssafy.eeum.domain.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.iot.entity.FallEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.ssafy.eeum.domain.iot.entity.FallEvent.StatusType;

/**
 * 낙상 감지 이벤트(FallEvent) 데이터에 접근하기 위한 레포지토리 인터페이스입니다.
 * 사고 이력 조회, 상태별 필터링 등의 기능을 지원합니다.
 * 
 * @summary 낙상 이벤트 레포지토리
 */
public interface FallEventRepository extends JpaRepository<FallEvent, Integer> {

    /**
     * 영상 파일 경로를 기준으로 낙상 이벤트를 조회합니다.
     * 
     * @summary 영상 경로 기반 이벤트 조회
     * @param videoPath S3 영상 경로
     * @return 낙상 이벤트 정보 (Optional)
     */
    Optional<FallEvent> findByVideoPath(String videoPath);

    /**
     * 특정 가족 그룹에서 특정 상태를 가진 가장 최근의 낙상 이벤트를 조회합니다.
     * 
     * @summary 최신 상테별 이벤트 조회
     * @param familyId   가족 그룹 식별자
     * @param statusType 조회할 상태
     * @return 낙상 이벤트 정보 (Optional)
     */
    Optional<FallEvent> findTopByFamilyIdAndStatusTypeOrderByCreatedAtDesc(Integer familyId, StatusType statusType);

    /**
     * 특정 상태이면서 일정 시간 이전에 생성된 이벤트를 조회합니다. (타임아웃 처리용)
     * 
     * @summary 타임아웃 대상 이벤트 조회
     * @param statusType 조회할 상태
     * @param createdAt  기준 생성 시간
     * @return 낙상 이벤트 리스트
     */
    List<FallEvent> findByStatusTypeAndCreatedAtBefore(StatusType statusType, LocalDateTime createdAt);

    /**
     * 특정 가족 그룹의 모든 낙상 이력을 최신순으로 조회합니다.
     * 
     * @summary 가족별 낙상 이력 조회
     * @param familyId 가족 그룹 식별자
     * @return 낙상 이벤트 리스트
     */
    List<FallEvent> findByFamilyIdOrderByCreatedAtDesc(Integer familyId);
}
