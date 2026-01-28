package org.ssafy.eeum.domain.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ssafy.eeum.domain.schedule.entity.Schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Integer> {
        List<Schedule> findByFamilyIdAndStartAtLessThanEqualAndEndAtGreaterThanEqualAndDeletedAtIsNull(
                        Integer familyId, LocalDate endDate, LocalDate startDate);

        /**
         * 특정 그룹의 일정 후보군 조회
         * 1. 반복 설정이 있는 모든 일정 (부모 일정)
         * 2. 반복은 없지만 부모 ID가 있는 일정 (개별 수정된 일정)
         * 3. 특정 기간 내에 걸쳐 있는 일반 일정
         *
         */
        @Query("SELECT s FROM Schedule s WHERE s.family.id = :familyId AND s.deletedAt IS NULL " +
                        "AND (" +
                        "  (s.repeatType != 'NONE' AND (s.recurrenceEndAt IS NULL OR s.recurrenceEndAt >= :startDate)) "
                        +
                        "  OR s.isLunar = true " +
                        "  OR s.parentId IS NOT NULL " +
                        "  OR (s.startAt <= :endDate AND s.endAt >= :startDate)" +
                        ")")
        List<Schedule> findCandidates(@Param("familyId") Integer familyId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        /**
         * 특정 반복 일정의 특정 날짜에 대한 자식 일정이 존재하는지 확인
         */
        Optional<Schedule> findByParentIdAndStartAtAndDeletedAtIsNull(Integer parentId, LocalDate startAt);

        /**
         * 특정 부모 일정에 속한 모든 자식 일정 조회
         */
        List<Schedule> findByParentIdAndDeletedAtIsNull(Integer parentId);
}