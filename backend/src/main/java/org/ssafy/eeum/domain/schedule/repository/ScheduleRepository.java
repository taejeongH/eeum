package org.ssafy.eeum.domain.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ssafy.eeum.domain.schedule.entity.Schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 가족 일정 데이터에 접근하기 위한 레포지토리 인터페이스입니다.
 * 기간별 일정 조회, 필터링 조회 등의 쿼리 메서드를 포함합니다.
 * 
 * @summary 가족 일정 레포지토리
 */
public interface ScheduleRepository extends JpaRepository<Schedule, Integer> {

        /**
         * 특정 기간 내에 시작하거나 종료되는 일정을 조회합니다.
         * 
         * @summary 기간별 일정 조회
         * @param familyId  가족 그룹 식별자
         * @param endDate   종료 기준일
         * @param startDate 시작 기준일
         * @return 일정 리스트
         */
        List<Schedule> findByFamilyIdAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                        Integer familyId, LocalDate endDate, LocalDate startDate);

        /**
         * 특정 월의 일정을 계산하기 위한 후보군 일정을 조회합니다.
         * 1. 반복 설정이 있는 모든 부모 일정
         * 2. 음력 일정
         * 3. 개별 수정된 예외 일정 (ParentId 보유)
         * 4. 해당 기간 내에 걸쳐 있는 일반 일정
         * 
         * @summary 월간 일정 후보군 조회
         * @param familyId  가족 그룹 식별자
         * @param startDate 시작 일시
         * @param endDate   종료 일시
         * @return 후보 일정 리스트
         */
        @Query("SELECT s FROM Schedule s WHERE s.family.id = :familyId " +
                        "AND (" +
                        "  (s.repeatType != 'NONE' AND (s.recurrenceEndAt IS NULL OR s.recurrenceEndAt >= :startDate)) "
                        +
                        "  OR s.isLunar = true " +
                        "  OR s.parentId IS NOT NULL " +
                        "  OR (s.startAt <= :endDate AND s.endAt >= :startDate)" +
                        ")")
        List<Schedule> findCandidates(@Param("familyId") Integer familyId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        /**
         * 특정 가족의 일정 중 시작 날짜가 지정된 범위 내에 있는 일정을 조회합니다.
         * 
         * @summary 시작일 범위 기반 일정 조회
         * @param familyId  가족 그룹 식별자
         * @param startDate 시작 범위
         * @param endDate   종료 범위
         * @return 일정 리스트
         */
        List<Schedule> findByFamilyIdAndStartAtBetween(
                        @Param("familyId") Integer familyId,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        /**
         * 부모 일정 ID와 특정 시작 날짜를 기준으로 일정을 조회합니다.
         * 주로 반복 일정 중 특정 예외 케이스를 찾을 때 사용됩니다.
         * 
         * @summary 부모ID 및 일자 기반 일정 조회
         * @param parentId 부모 일정 식별자
         * @param startAt  시작 날짜
         * @return 일정 (Optional)
         */
        Optional<Schedule> findByParentIdAndStartAt(Integer parentId, LocalDate startAt);

        /**
         * 특정 부모 일정에 속한 모든 하위(예외) 일정들을 조회합니다.
         * 
         * @summary 부모 ID 기반 하위 일정 전체 조회
         * @param parentId 부모 일정 식별자
         * @return 하위 일정 리스트
         */
        List<Schedule> findByParentId(Integer parentId);
}