package org.ssafy.eeum.domain.medication.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.ssafy.eeum.domain.medication.entity.MedicationPlan;
import org.ssafy.eeum.domain.medication.entity.MedicationPlanTime;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationAlarmRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String ALARM_KEY = "medication:alarms";

    public void scheduleAlarm(MedicationPlan plan) {
        if (plan.getMedicationPlanTimes() == null || plan.getMedicationPlanTimes().isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        ZoneId zoneId = ZoneId.of("Asia/Seoul");

        for (MedicationPlanTime planTime : plan.getMedicationPlanTimes()) {
            LocalDateTime occurrence = now.with(planTime.getNotificationTime());
            if (occurrence.isBefore(now)) {
                occurrence = occurrence.plusDays(1);
            }

            
            if (plan.getEndDate() != null && occurrence.toLocalDate().isAfter(plan.getEndDate())) {
                continue;
            }

            long timestamp = occurrence.atZone(zoneId).toInstant().toEpochMilli();

            String member = String.format("%d:%s", plan.getId(), planTime.getNotificationTime().toString());
            redisTemplate.opsForZSet().add(ALARM_KEY, member, timestamp);
        }
    }

    public void cancelAlarms(Long planId) {
        Set<Object> members = redisTemplate.opsForZSet().range(ALARM_KEY, 0, -1);
        if (members != null) {
            Set<Object> toRemove = members.stream()
                    .filter(m -> m.toString().startsWith(planId + ":"))
                    .collect(Collectors.toSet());

            if (!toRemove.isEmpty()) {
                redisTemplate.opsForZSet().remove(ALARM_KEY, toRemove.toArray());
            }
        }
    }

    public Set<String> popDueAlarms(long currentTimestamp) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Set<Object> dueMembers = zSetOps.rangeByScore(ALARM_KEY, 0, currentTimestamp);

        if (dueMembers != null && !dueMembers.isEmpty()) {
            zSetOps.remove(ALARM_KEY, dueMembers.toArray());
            return dueMembers.stream().map(Object::toString).collect(Collectors.toSet());
        }

        return Set.of();
    }
}
