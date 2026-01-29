package org.ssafy.eeum.domain.medication.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.medication.entity.MedicationPlan;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.LocalTime;

public interface MedicationRepository extends JpaRepository<MedicationPlan, Long> {
    List<MedicationPlan> findByGroupId(Long groupId);

    @Query("SELECT distinct p FROM MedicationPlan p JOIN p.notificationTimes t " +
            "WHERE t.notificationTime = :time " +
            "AND p.startDate <= :date AND p.endDate >= :date")
    List<MedicationPlan> findPlansByTimeAndDate(@Param("time") LocalTime time, @Param("date") LocalDate date);
}
