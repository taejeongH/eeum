package org.ssafy.eeum.domain.medication.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.medication.dto.request.MedicationRequest;
import org.ssafy.eeum.domain.medication.dto.response.MedicationResponse;
import org.ssafy.eeum.domain.medication.entity.MedicationPlan;
import org.ssafy.eeum.domain.medication.entity.MedicationPlanTime;
import org.ssafy.eeum.domain.medication.repository.MedicationRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationService {

    private final MedicationRepository medicationRepository;

    @Transactional
    public List<Long> createMedicationPlans(Long familyId, List<MedicationRequest> requests) {
        return requests.stream().map(request -> {
            MedicationPlan medicationPlan = MedicationPlan.builder()
                    .groupId(familyId)
                    .medicineName(request.getMedicineName())
                    .cycleType(request.getCycleType())
                    .totalDosesDay(request.getTotalDosesDay())
                    .cycleValue(request.getCycleValue())
                    .daysOfWeek(request.getDaysOfWeek())
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .build();

            if (request.getNotificationTimes() != null) {
                for (LocalTime time : request.getNotificationTimes()) {
                    MedicationPlanTime planTime = MedicationPlanTime.builder()
                            .notificationTime(time)
                            .build();
                    medicationPlan.addMedicationPlanTime(planTime);
                }
            }

            medicationRepository.save(medicationPlan);
            return medicationPlan.getId();
        }).collect(Collectors.toList());
    }

    public MedicationResponse getMedicationPlan(Long medicationPlanId) {
        MedicationPlan medicationPlan = medicationRepository.findById(medicationPlanId)
                .orElseThrow(() -> new IllegalArgumentException("해당 약 정보가 존재하지 않습니다. id=" + medicationPlanId));
        return MedicationResponse.from(medicationPlan);
    }
    
    public List<MedicationResponse> getMedicationPlansByGroupId(Long groupId) {
        return medicationRepository.findByGroupId(groupId).stream()
                .map(MedicationResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteMedicationPlan(Long medicationId) {
        MedicationPlan medicationPlan = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 약 정보가 존재하지 않습니다. id=" + medicationId));
        medicationRepository.delete(medicationPlan);
    }

    @Transactional
    public void updateMedicationPlan(Long medicationId, MedicationRequest request) {
        MedicationPlan medicationPlan = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 약 정보가 존재하지 않습니다. id=" + medicationId));

        // Update fields
        medicationPlan.update(
            request.getMedicineName(),
            request.getCycleType(),
            request.getCycleValue(),
            request.getDaysOfWeek(),
            request.getTotalDosesDay(),
            request.getStartDate(),
            request.getEndDate()
        );
        
        // Update Times (Clear and Re-add)
        medicationPlan.getNotificationTimes().clear();
        for (java.time.LocalTime time : request.getNotificationTimes()) {
            medicationPlan.addNotificationTime(time);
        }
    }
}
