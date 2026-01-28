package org.ssafy.eeum.domain.medication.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.eeum.domain.medication.entity.MedicationPlan;

import java.util.List;

public interface MedicationRepository extends JpaRepository<MedicationPlan, Long> {
    List<MedicationPlan> findByGroupId(Long groupId);
}
