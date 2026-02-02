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

    /**
     * 알람 스케줄링 (Redis ZSET에 추가)
     * Score: Milliseconds Timestamp
     * Member: MedicationPlan ID
     */
    public void scheduleAlarm(MedicationPlan plan) {
        if (plan.getMedicationPlanTimes() == null || plan.getMedicationPlanTimes().isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        ZoneId zoneId = ZoneId.of("Asia/Seoul");

        for (MedicationPlanTime planTime : plan.getMedicationPlanTimes()) {
            // 해당 알람의 다음 발생 시각 계산 (오늘 혹은 내일)
            LocalDateTime occurrence = now.with(planTime.getNotificationTime());
            if (occurrence.isBefore(now)) {
                occurrence = occurrence.plusDays(1);
            }

            // 복약 기간 체크 (종료일이 지난 경우 제외)
            if (plan.getEndDate() != null && occurrence.toLocalDate().isAfter(plan.getEndDate())) {
                continue;
            }

            long timestamp = occurrence.atZone(zoneId).toInstant().toEpochMilli();

            // ZSET에 추가 (Key: alarm:planId, Member는 중복 가능성을 고려해 planId:time 형식 권장하나 단순하게
            // planId로 처리)
            // 여러 개의 시간을 가질 수 있으므로 member를 고유하게 생성 (planId:HHmm)
            String member = String.format("%d:%s", plan.getId(), planTime.getNotificationTime().toString());
            redisTemplate.opsForZSet().add(ALARM_KEY, member, timestamp);

            log.debug("[RedisAlarm] Scheduled: {} at {}", member, occurrence);
        }
    }

    /**
     * 특정 수동 삭제/수정 시 관련 알람 제거
     */
    public void cancelAlarms(Long planId) {
        // 해당 planId로 시작하는 모든 member 제거
        Set<Object> members = redisTemplate.opsForZSet().range(ALARM_KEY, 0, -1);
        if (members != null) {
            Set<Object> toRemove = members.stream()
                    .filter(m -> m.toString().startsWith(planId + ":"))
                    .collect(Collectors.toSet());

            if (!toRemove.isEmpty()) {
                redisTemplate.opsForZSet().remove(ALARM_KEY, toRemove.toArray());
                log.debug("[RedisAlarm] Cancelled {} alarms for PlanId: {}", toRemove.size(), planId);
            }
        }
    }

    /**
     * 현재 시각까지 발송되어야 할 알람 목록 조회 및 제거
     */
    public Set<String> popDueAlarms(long currentTimestamp) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

        // 0부터 현재 타임스탬프까지의 데이터 조회
        Set<Object> dueMembers = zSetOps.rangeByScore(ALARM_KEY, 0, currentTimestamp);

        if (dueMembers != null && !dueMembers.isEmpty()) {
            // 조회된 데이터 삭제 (다음에 중복 발송되지 않도록)
            zSetOps.remove(ALARM_KEY, dueMembers.toArray());
            return dueMembers.stream().map(Object::toString).collect(Collectors.toSet());
        }

        return Set.of();
    }
}
