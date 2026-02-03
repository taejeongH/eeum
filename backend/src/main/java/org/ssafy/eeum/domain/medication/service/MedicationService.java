package org.ssafy.eeum.domain.medication.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationService {

    private final org.ssafy.eeum.global.infra.mqtt.MqttService mqttService;
    private final org.ssafy.eeum.domain.iot.repository.IotDeviceRepository iotDeviceRepository;
    private final MedicationRepository medicationRepository;
    private final MedicationAlarmRedisService medicationAlarmRedisService;

    @jakarta.annotation.PostConstruct
    @Transactional
    public void bootstrapRedis() {
        log.info("[MedicationService] Bootstrapping Redis Alarm Cache from DB...");
        List<MedicationPlan> allPlans = medicationRepository.findAllWithTimes();
        for (MedicationPlan plan : allPlans) {
            medicationAlarmRedisService.scheduleAlarm(plan);
        }
        log.info("[MedicationService] Redis Bootstrap Complete. Scheduled {} plans.", allPlans.size());
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 * * * * *")
    public void sendMedicationAlarm() {
        long currentTimestamp = System.currentTimeMillis();
        java.util.Set<String> dueAlarms = medicationAlarmRedisService.popDueAlarms(currentTimestamp);

        if (dueAlarms.isEmpty()) {
            return;
        }

        log.debug("[MedicationAlarm] Redis found {} due alarms", dueAlarms.size());

        for (String alarmMember : dueAlarms) {
            try {
                // alarmMember 형식: "planId:HH:mm"
                Long planId = Long.parseLong(alarmMember.split(":")[0]);
                MedicationPlan plan = medicationRepository.findById(planId).orElse(null);

                if (plan == null || !isDueToday(plan, java.time.LocalDate.now())) {
                    continue;
                }

                // 기기 조회 및 알림 전송
                String summaryText = plan.getMedicineName();
                String content = String.format("복약 시간입니다. %s 약을 드세요.", summaryText);

                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("medication_list", java.util.List.of(plan.getMedicineName()));
                data.put("text_message", summaryText);

                List<org.ssafy.eeum.domain.iot.entity.IotDevice> devices = iotDeviceRepository
                        .findAllByFamilyId(plan.getGroupId().intValue());

                for (org.ssafy.eeum.domain.iot.entity.IotDevice device : devices) {
                    log.info("[MedicationAlarm] Sending Redis-based alarm to SN: {}, PlanId: {}",
                            device.getSerialNumber(), planId);
                    mqttService.sendAlarm(device.getSerialNumber(), "medication", content, data);
                }

                // 다음 알람 예약 (복약 성공 여부와 상관없이 주기적으로 다시 예약)
                medicationAlarmRedisService.scheduleAlarm(plan);

            } catch (Exception e) {
                log.error("[MedicationAlarm] Failed to process Redis alarm {}: {}", alarmMember, e.getMessage());
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
            medicationAlarmRedisService.scheduleAlarm(medicationPlan);
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
        medicationAlarmRedisService.cancelAlarms(medicationId);
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

        // Redis 스케줄 갱신
        medicationAlarmRedisService.cancelAlarms(medicationId);
        medicationAlarmRedisService.scheduleAlarm(medicationPlan);
    }
}
