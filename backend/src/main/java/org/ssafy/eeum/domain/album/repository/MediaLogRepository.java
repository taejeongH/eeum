package org.ssafy.eeum.domain.album.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.album.entity.MediaLog;

import java.util.List;

/**
 * 미디어 변경 이력(로그)에 대한 데이터 접근을 담당하는 리포지토리입니다.
 */
public interface MediaLogRepository extends JpaRepository<MediaLog, Integer> {

    /**
     * 특정 가족에 대해 지정된 로그 ID보다 큰(최신인) 로그 목록을 조회합니다.
     * 
     * @summary 최신 로그 목록 조회
     * @param groupId   가족 식별자
     * @param lastLogId 기준이 되는 로그 ID
     * @return 미디어 로그 리스트
     */
    List<MediaLog> findByGroupIdAndIdGreaterThan(Integer groupId, Integer lastLogId);
}
