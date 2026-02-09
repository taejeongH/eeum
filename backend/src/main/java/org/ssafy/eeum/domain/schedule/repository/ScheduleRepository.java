package org.ssafy.eeum.domain.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ssafy.eeum.domain.schedule.entity.Schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Integer> {
        List<Schedule> findByFamilyIdAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
                        Integer familyId, LocalDate endDate, LocalDate startDate);

        
        @Query("SELECT s FROM Schedule s WHERE s.family.id = :familyId " +
                        "AND (" +
                        "  (s.repeatType != 'NONE' AND (s.recurrenceEndAt IS NULL OR s.recurrenceEndAt >= :startDate)) "
                        +
                        "  OR s.isLunar = true " +
                        "  OR s.parentId IS NOT NULL " +
                        "  OR (s.startAt <= :endDate AND s.endAt >= :startDate)" +
                        ")")
        List<Schedule> findCandidates(@Param("familyId") Integer familyId,
                        @Param("startDate") java.time.LocalDateTime startDate,
                        @Param("endDate") java.time.LocalDateTime endDate);

        Optional<Schedule> findByParentIdAndStartAt(Integer parentId, LocalDate startAt);

        List<Schedule> findByParentId(Integer parentId);
}