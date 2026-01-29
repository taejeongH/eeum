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

    private final org.ssafy.eeum.global.infra.mqtt.MqttService mqttService;
    private final org.ssafy.eeum.domain.iot.repository.IotDeviceRepository iotDeviceRepository;
    private final MedicationRepository medicationRepository;

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 * * * * *")
    public void sendMedicationAlarm() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDate today = now.toLocalDate();
        java.time.LocalTime currentTime = now.toLocalTime().withSecond(0).withNano(0);

        // 현재 시간에 복약해야 할 계획 조회
        List<MedicationPlan> plans = medicationRepository.findPlansByTimeAndDate(currentTime, today);

        // 가족 그룹별로 계획을 그룹화
        java.util.Map<Long, List<MedicationPlan>> plansByGroup = plans.stream()
                .collect(Collectors.groupingBy(MedicationPlan::getGroupId));

        for (java.util.Map.Entry<Long, List<MedicationPlan>> entry : plansByGroup.entrySet()) {
            Long groupId = entry.getKey();
            List<MedicationPlan> allPlans = entry.getValue();

            // 오늘 날짜 및 주기에 맞춰 해당되는 계획만 필터링
            List<MedicationPlan> groupPlans = allPlans.stream()
                    .filter(plan -> isDueToday(plan, today))
                    .collect(Collectors.toList());

            if (groupPlans.isEmpty())
                continue;

            // 약 이름 목록 추출
            List<String> medicineNames = groupPlans.stream()
                    .map(MedicationPlan::getMedicineName)
                    .collect(Collectors.toList());

            // 요약 텍스트 생성 (예: "고혈압약 외 2개")
            String summaryText;
            if (medicineNames.size() <= 1) {
                summaryText = medicineNames.get(0);
            } else {
                summaryText = String.format("%s 외 %d개", medicineNames.get(0), medicineNames.size() - 1);
            }

            // 알림 콘텐츠 생성 (예: "복약 시간입니다. 고혈압약 외 2개 약을 드세요.")
            String content = String.format("복약 시간입니다. %s 약을 드세요.", summaryText);

            // 알림 데이터 구성
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("medication_list", medicineNames);
            data.put("text_message", summaryText);

            // 해당 가족의 IoT 기기 목록 조회
            List<org.ssafy.eeum.domain.iot.entity.IotDevice> devices = iotDeviceRepository
                    .findAllByFamilyId(groupId.intValue());

            for (org.ssafy.eeum.domain.iot.entity.IotDevice device : devices) {
                // MQTT 알림 전송
                mqttService.sendAlarm(device.getSerialNumber(), "medication", content, data);
            }
        }
    }

    private boolean isDueToday(MedicationPlan plan, java.time.LocalDate today) {
        switch (plan.getCycleType()) {
            case DAILY:
                return true;
            case WEEKLY:
                // daysOfWeek는 비트마스크 (Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64)
                int dayBit = 1 << (today.getDayOfWeek().getValue() - 1);
                return (plan.getDaysOfWeek() & dayBit) != 0;
            case INTERVAL:
                // start_date부터 cycle_value(일수) 간격으로 체크
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(plan.getStartDate(), today);
                int interval = Integer.parseInt(plan.getCycleValue());
                return daysBetween >= 0 && daysBetween % interval == 0;
            default:
                return false;
        }
    }

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
                request.getEndDate());

        // Update Times (Clear and Re-add)
        medicationPlan.getNotificationTimes().clear();
        for (java.time.LocalTime time : request.getNotificationTimes()) {
            medicationPlan.addNotificationTime(time);
        }
    }
}
