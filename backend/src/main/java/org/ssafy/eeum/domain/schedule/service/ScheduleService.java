package org.ssafy.eeum.domain.schedule.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.schedule.dto.ScheduleRequestDTO;
import org.ssafy.eeum.domain.schedule.dto.ScheduleResponseDTO;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.domain.schedule.entity.RepeatType;
import org.ssafy.eeum.domain.schedule.entity.Schedule;
import org.ssafy.eeum.domain.schedule.repository.ScheduleRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.redis.RedisService;
import org.ssafy.eeum.global.util.CalendarUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final RedisService redisService;
    private final org.ssafy.eeum.domain.iot.service.IotSyncService iotSyncService;
    private final org.ssafy.eeum.global.infra.mqtt.MqttService mqttService;
    private final org.ssafy.eeum.domain.iot.repository.IotDeviceRepository iotDeviceRepository;

    // 월간 일정 조회 (캐시 적용)
    public List<ScheduleResponseDTO> getMonthlySchedules(Integer familyId, int year, int month, String category,
            String keyword, String targetPerson, Boolean isVisited) {

        YearMonth targetMonth = YearMonth.of(year, month);

        // 필터 조건이 있으면 캐시 사용하지 않음
        boolean hasFilter = (category != null || keyword != null || targetPerson != null || isVisited != null);

        if (!hasFilter) {
            String cacheKey = "family:" + familyId + ":schedule:" + targetMonth;
            List<ScheduleResponseDTO> cachedData = redisService.getList(cacheKey, ScheduleResponseDTO.class);
            if (cachedData != null) {
                return cachedData;
            }
        }

        List<ScheduleResponseDTO> result = calculateMonthlySchedules(familyId, targetMonth, category, keyword,
                targetPerson, isVisited);

        if (!hasFilter) {
            String cacheKey = "family:" + familyId + ":schedule:" + targetMonth;
            redisService.setList(cacheKey, result, Duration.ofDays(1));
        }

        return result;
    }

    public ScheduleResponseDTO getSchedule(Integer familyId, String scheduleId) {
        if (scheduleId.contains("_")) {
            String[] parts = scheduleId.split("_");
            Integer parentId = Integer.parseInt(parts[0]);
            LocalDate targetDate = LocalDate.parse(parts[1]);

            // 1. 해당 날짜에 예외(수정/삭제) 일정이 있는지 확인
            Optional<Schedule> exceptionSchedule = scheduleRepository.findAll().stream()
                    .filter(s -> s.getParentId() != null && s.getParentId().equals(parentId)
                            && s.getStartAt() != null && s.getStartAt().toLocalDate().equals(targetDate))
                    .findFirst();

            if (exceptionSchedule.isPresent()) {
                return convertToResponse(exceptionSchedule.get(), scheduleId, targetDate, true);
            }

            // 2. 없으면 부모 일정을 기반으로 가상 일정 생성
            Schedule parent = scheduleRepository.findById(parentId)
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

            // 유효성 검사 (반복 범위 등)
            List<LocalDate> occurrences = calculateOccurrenceDates(parent, targetDate, targetDate);
            if (occurrences.isEmpty()) {
                throw new CustomException(ErrorCode.ENTITY_NOT_FOUND);
            }

            return convertToResponse(parent, scheduleId, targetDate, false);

        } else {
            Schedule schedule = scheduleRepository.findById(Integer.parseInt(scheduleId))
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

            // 일반 일정인 경우 그대로 반환, 반복 일정의 부모인 경우 첫번째 날짜 등 로직 필요할 수 있으나
            // 상세 조회는 보통 ID로 특정된 것을 조회하므로 그대로 반환.
            // 만약 반복 일정의 부모(MASTER)를 조회하는 경우라면?
            // 기획 상 목록에서 클릭 시 virtualId를 가져오므로, 여기서는 물리적 ID 조회만 처리.
            return convertToResponse(schedule, scheduleId, schedule.getStartAt().toLocalDate(), false);
        }
    }

    // DB 데이터를 읽어 해당 월의 실제 날짜들로 확장
    private List<ScheduleResponseDTO> calculateMonthlySchedules(Integer familyId, YearMonth ym, String category,
            String keyword, String targetPerson, Boolean isVisited) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        // LocalDate를 LocalDateTime으로 변환 (하루의 시작과 끝)
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        List<Schedule> candidates = scheduleRepository.findCandidates(familyId, startDateTime, endDateTime);

        // 1차 필터링: 부모 레벨에서 가능한 필터 적용 (카테고리, 제목/내용 등)
        // 반복 일정의 경우 원본이 매칭되면 자식도 매칭되는 것으로 간주
        if (category != null) {
            candidates = candidates.stream()
                    .filter(s -> s.getCategoryType().name().equalsIgnoreCase(category))
                    .toList();
        }

        if (keyword != null) {
            String finalKeyword = keyword.toLowerCase();
            candidates = candidates.stream()
                    .filter(s -> s.getTitle().toLowerCase().contains(finalKeyword) ||
                            (s.getDescription() != null && s.getDescription().toLowerCase().contains(finalKeyword)))
                    .toList();
        }

        if (targetPerson != null) {
            String finalTarget = targetPerson;
            candidates = candidates.stream()
                    .filter(s -> s.getTargetPerson() != null && s.getTargetPerson().equals(finalTarget))
                    .toList();
        }

        // isVisited 필터
        if (isVisited != null) {
            boolean visitFilter = isVisited;
            candidates = candidates.stream()
                    .filter(s -> {
                        if (s.getIsVisited() == null)
                            return !visitFilter; // null이면 false 취급 (안감)
                        return s.getIsVisited() == visitFilter;
                    })
                    .toList();
        }

        Set<String> modifiedMask = scheduleRepository.findCandidates(familyId, startDateTime, endDateTime).stream()
                .filter(s -> s.getParentId() != null)
                .map(s -> s.getParentId() + "_" + s.getStartAt())
                .collect(Collectors.toSet());

        List<ScheduleResponseDTO> result = new ArrayList<>();

        for (Schedule s : candidates) {
            // EXCLUDED 일정은 결과에 포함하지 않음 (마스킹 용도로만 사용)
            if ("EXCLUDED".equals(s.getTitle())) {
                continue;
            }
            // 예외 일정(수정된 자식)은 이미 candidates에 포함되어 있음.
            // 다만, 부모 일정 처리 시 '수정된 날짜'에 대해서는 가상 일정을 생성하지 말아야 함.
            //
            // 주의: 수정된 자식 일정의 parentId를 가진 부모 일정 s에 대해,
            // calculateOccurrenceDates는 부모의 규칙대로 날짜를 생성함.
            // 이 중 자식이 존재하는(수정된) 날짜는 modifiedMask에 의해 걸러져야 함.
            // s가 부모인 경우 -> occurrences 생성 -> mask 체크 -> 결과 추가
            // s가 자식(수정됨)인 경우 -> occurrences는 단일 날짜 -> mask 체크 불필요(자기 자신이므로) -> 결과 추가
            // s가 일반 일정인 경우 -> occurrences는 단일 날짜 -> 결과 추가

            // 만약 s가 수정된 자식 일정이라면, getParentId() != null
            // 이 경우 calculateOccurrenceDates가 해당 날짜 1개만 반환하도록 되어 있어야 함 (RepeatType.NONE 이므로)

            List<LocalDate> occurrences = calculateOccurrenceDates(s, start, end);

            for (LocalDate date : occurrences) {
                String virtualId = (s.getRepeatType() == RepeatType.NONE) ? s.getId().toString()
                        : s.getId() + "_" + date;

                // 만약 s가 부모 일정이고, 해당 날짜에 대해 수정된 내역(자식)이 있다면 건너뜀
                // 마스킹 로직 수정: modifiedMask는 "parentId_date" 형식.
                // s가 부모인 경우 s.getId() == parentId.
                // 따라서 s.getId() + "_" + date 가 mask에 있으면, 해당 날짜는 자식(수정본)이 대체하므로 부모꺼는 스킵.
                if (s.getRepeatType() != RepeatType.NONE && modifiedMask.contains(s.getId() + "_" + date)) {
                    continue;
                }

                result.add(convertToResponse(s, virtualId, date, s.getParentId() != null));
            }
        }
        return result.stream().sorted(Comparator.comparing(ScheduleResponseDTO::getStartAt)).toList();
    }

    // 반복 규칙과 음력 설정을 고려하여 해당 월 내의 발생 날짜들 계산
    private List<LocalDate> calculateOccurrenceDates(Schedule s, LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate baseDate = s.getStartAt().toLocalDate();
        LocalDate limitDate = s.getRecurrenceEndAt() != null ? s.getRecurrenceEndAt().toLocalDate() : null;
        LocalDate targetDate;

        if (s.getRepeatType() == RepeatType.NONE) {
            if (Boolean.TRUE.equals(s.getIsLunar())) {
                targetDate = CalendarUtils.convertLunarToSolar(baseDate);
            } else {
                targetDate = baseDate;
            }
        } else {
            if (Boolean.TRUE.equals(s.getIsLunar())) {
                // 음력 반복의 경우, 해당 기간(start~end) 내에 존재하는 양력 날짜를 찾아야 함.
                // 단순히 baseDate의 연도를 start의 연도로 바꾸는 것만으로는 부족할 수 있음 (연말/연초 등).
                // 하지만 현재 로직을 유지하면서 확장.
                targetDate = CalendarUtils.convertLunarToSolar(baseDate.withYear(start.getYear()));
                // 만약 targetDate가 start/end 범위를 벗어나면? 보정 필요?
                // 일단 기존 로직 따름.
            } else {
                targetDate = baseDate.withYear(start.getYear());
            }
        }

        // 반복 없음 (NONE)
        if (s.getRepeatType() == RepeatType.NONE) {
            // start, end 범위 내인지 확인
            // baseDate가 아닌 targetDate(음력변환됨) 기준
            if (!targetDate.isBefore(start) && !targetDate.isAfter(end)) {
                dates.add(targetDate);
            }
        }

        // 매년 반복 (YEARLY)
        else if (s.getRepeatType() == RepeatType.YEARLY) {
            // targetDate는 start의 연도로 설정됨.
            // 1. targetDate 확인
            if (isValidOccurrence(targetDate, start, end, baseDate, limitDate))
                dates.add(targetDate);

            // 2. targetDate의 전년도, 다음년도도 확인 (경계 근처일 수 있음 - 특히 음력)
            LocalDate prev = targetDate.minusYears(1);
            if (Boolean.TRUE.equals(s.getIsLunar()))
                prev = CalendarUtils.convertLunarToSolar(baseDate.withYear(start.getYear() - 1));
            if (isValidOccurrence(prev, start, end, baseDate, limitDate))
                dates.add(prev);

            LocalDate next = targetDate.plusYears(1);
            if (Boolean.TRUE.equals(s.getIsLunar()))
                next = CalendarUtils.convertLunarToSolar(baseDate.withYear(start.getYear() + 1));
            if (isValidOccurrence(next, start, end, baseDate, limitDate))
                dates.add(next);
        }

        return dates.stream().distinct().sorted().toList();
    }

    private boolean isValidOccurrence(LocalDate date, LocalDate start, LocalDate end, LocalDate base, LocalDate limit) {
        return !date.isBefore(start) && !date.isAfter(end) && !date.isBefore(base)
                && (limit == null || !date.isAfter(limit));
    }

    private ScheduleResponseDTO convertToResponse(Schedule s, String id, LocalDate date, boolean isModified) {
        // 원본 일정의 시간 정보 추출
        LocalTime time = s.getStartAt().toLocalTime();
        LocalDateTime startDateTime = date.atTime(time);
        LocalDateTime endDateTime = date.atTime(s.getEndAt().toLocalTime());

        return ScheduleResponseDTO.builder()
                .scheduleId(id)
                .title(s.getTitle())
                .startAt(startDateTime)
                .endAt(endDateTime)
                .categoryType(s.getCategoryType())
                .description(s.getDescription())
                .visitorName(s.getVisitorName())
                .visitPurpose(s.getVisitPurpose())
                .isVisited(s.getIsVisited())
                .repeatType(s.getRepeatType())
                .isLunar(s.getIsLunar())
                .isModified(isModified)
                .targetPerson(s.getTargetPerson())
                .build();
    }

    // 일정 등록
    @Transactional
    public void createSchedule(Integer userId, Integer familyId, ScheduleRequestDTO dto) {
        if (dto.getStartAt().isAfter(dto.getEndAt())) {
            throw new CustomException(ErrorCode.INVALID_DATE_RANGE);
        }
        if ("EXCLUDED".equalsIgnoreCase(dto.getTitle())) {
            throw new CustomException(ErrorCode.RESERVED_TITLE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "가족 그룹을 찾을 수 없습니다."));

        Schedule schedule = Schedule.builder()
                .family(family)
                .creator(user)
                .title(dto.getTitle())
                .startAt(dto.getStartAt())
                .endAt(dto.getEndAt())
                .categoryType(dto.getCategoryType())
                .description(dto.getDescription())
                .repeatType(dto.getRepeatType())
                .isLunar(dto.getIsLunar())
                .targetPerson(dto.getTargetPerson())
                .visitorName(dto.getVisitorName())
                .visitPurpose(dto.getVisitPurpose())
                .isVisited(false)
                .recurrenceEndAt(dto.getRecurrenceEndAt())
                .build();

        scheduleRepository.save(schedule);
        invalidateCache(familyId, dto.getStartAt().toLocalDate());
        iotSyncService.notifyUpdate(familyId, "schedule");
    }

    // 일정 수정
    @Transactional
    public void updateSchedule(Integer userId, Integer familyId, String scheduleId, ScheduleRequestDTO dto) {
        if (dto.getStartAt().isAfter(dto.getEndAt())) {
            throw new CustomException(ErrorCode.INVALID_DATE_RANGE);
        }
        if ("EXCLUDED".equalsIgnoreCase(dto.getTitle())) {
            throw new CustomException(ErrorCode.RESERVED_TITLE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        ParsedScheduleId parsedId = parseScheduleId(scheduleId);

        if (parsedId.isVirtual()) {
            Integer parentId = parsedId.parentId();
            LocalDate targetDate = parsedId.date();

            if (!targetDate.equals(dto.getStartAt().toLocalDate())) {
                Schedule parent = scheduleRepository.findById(parentId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
                createAndSaveExclusion(user, familyId, parent, targetDate);
            }

            Schedule schedule = scheduleRepository.findAll().stream()
                    .filter(s -> s.getParentId() != null && s.getParentId().equals(parentId)
                            && s.getStartAt() != null && s.getStartAt().toLocalDate().equals(targetDate))
                    .findFirst()
                    .orElseGet(() -> {
                        Schedule parent = scheduleRepository.findById(parentId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
                        LocalTime time = parent.getStartAt().toLocalTime();
                        return Schedule.builder()
                                .family(parent.getFamily())
                                .creator(user)
                                .parentId(parentId)
                                .categoryType(parent.getCategoryType())
                                .title(parent.getTitle())
                                .startAt(targetDate.atTime(time))
                                .endAt(targetDate.atTime(parent.getEndAt().toLocalTime()))
                                .build();
                    });

            schedule.update(
                    dto.getTitle(),
                    dto.getStartAt(),
                    dto.getEndAt(),
                    null,
                    dto.getDescription(),
                    dto.getVisitorName(),
                    dto.getVisitPurpose(),
                    RepeatType.NONE,
                    dto.getIsLunar(),
                    dto.getTargetPerson());

            scheduleRepository.save(schedule);
            invalidateCache(familyId, targetDate);
            invalidateCache(familyId, dto.getStartAt().toLocalDate());
            iotSyncService.notifyUpdate(familyId, "schedule");

        } else {
            Schedule schedule = scheduleRepository.findById(parsedId.dbId())
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

            LocalDateTime oldDate = schedule.getStartAt();

            schedule.update(
                    dto.getTitle(),
                    dto.getStartAt(),
                    dto.getEndAt(),
                    dto.getRecurrenceEndAt(),
                    dto.getDescription(),
                    dto.getVisitorName(),
                    dto.getVisitPurpose(),
                    dto.getRepeatType(),
                    dto.getIsLunar(),
                    dto.getTargetPerson());

            invalidateCache(familyId, oldDate.toLocalDate());
            invalidateCache(familyId, dto.getStartAt().toLocalDate());
            iotSyncService.notifyUpdate(familyId, "schedule");
        }
    }

    // 일정 삭제
    @Transactional
    public void deleteSchedule(Integer userId, Integer familyId, String scheduleId, boolean deleteAll) {
        ParsedScheduleId parsedId = parseScheduleId(scheduleId);

        if (deleteAll) {
            Integer targetId = parsedId.isVirtual() ? parsedId.parentId() : parsedId.dbId();

            Schedule schedule = scheduleRepository.findById(targetId)
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

            scheduleRepository.delete(schedule);
            invalidateCache(familyId, schedule.getStartAt().toLocalDate());
        } else {
            if (parsedId.isVirtual()) {
                Integer parentId = parsedId.parentId();
                LocalDate targetDate = parsedId.date();

                Schedule parent = scheduleRepository.findById(parentId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));

                createAndSaveExclusion(user, familyId, parent, targetDate);
                invalidateCache(familyId, targetDate);
            } else {
                Schedule schedule = scheduleRepository.findById(parsedId.dbId())
                        .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                if (schedule.getRepeatType() != RepeatType.NONE) {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));
                    createAndSaveExclusion(user, familyId, schedule, schedule.getStartAt().toLocalDate());
                    invalidateCache(familyId, schedule.getStartAt().toLocalDate());
                } else {
                    scheduleRepository.delete(schedule);
                    invalidateCache(familyId, schedule.getStartAt().toLocalDate());
                }
                iotSyncService.notifyUpdate(familyId, "schedule");
            }
        }
    }

    // 방문 상태 변경
    @Transactional
    public void updateVisitStatus(Integer userId, Integer familyId, String scheduleId, boolean visited) {
        ParsedScheduleId parsedId = parseScheduleId(scheduleId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (parsedId.isVirtual()) {
            Integer parentId = parsedId.parentId();
            LocalDate targetDate = parsedId.date();

            Schedule parent = scheduleRepository.findById(parentId)
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

            createAndSaveExclusion(user, familyId, parent, targetDate);
            createIndependentSchedule(user, familyId, parent, targetDate, visited);

            invalidateCache(familyId, targetDate);

        } else {
            Schedule schedule = scheduleRepository.findById(parsedId.dbId())
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

            if (schedule.getRepeatType() != RepeatType.NONE) {
                createAndSaveExclusion(user, familyId, schedule, schedule.getStartAt().toLocalDate());
                createIndependentSchedule(user, familyId, schedule, schedule.getStartAt().toLocalDate(), visited);

                invalidateCache(familyId, schedule.getStartAt().toLocalDate());

            } else {
                schedule.updateVisitStatus(visited);
            }
        }
    }

    public void invalidateCache(Integer familyId, LocalDate date) {
        String key = "family:" + familyId + ":schedule:" + YearMonth.from(date);
        redisService.deleteData(key);
    }

    public Schedule createAndSaveExclusion(User creator, Integer familyId, Schedule parent, LocalDate targetDate) {

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        LocalTime time = parent.getStartAt().toLocalTime();
        Schedule exclusion = Schedule.builder()
                .family(family)
                .creator(creator)
                .parentId(parent.getParentId() != null ? parent.getParentId() : parent.getId())
                .startAt(targetDate.atTime(time))
                .endAt(targetDate.atTime(parent.getEndAt().toLocalTime()))
                .categoryType(parent.getCategoryType())
                .repeatType(RepeatType.NONE)
                .title("EXCLUDED")
                .build();
        scheduleRepository.save(exclusion);
        return exclusion;
    }

    private void createIndependentSchedule(User creator, Integer familyId, Schedule source, LocalDate targetDate,
            boolean visited) {

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        LocalTime time = source.getStartAt().toLocalTime();
        Schedule newSchedule = Schedule.builder()
                .family(family)
                .creator(creator)
                .title(source.getTitle())
                .startAt(targetDate.atTime(time))
                .endAt(targetDate.atTime(source.getEndAt().toLocalTime()))
                .categoryType(source.getCategoryType())
                .description(source.getDescription())
                .repeatType(RepeatType.NONE)
                .isLunar(source.getIsLunar())
                .targetPerson(source.getTargetPerson())
                .visitorName(source.getVisitorName())
                .visitPurpose(source.getVisitPurpose())
                .isVisited(visited)
                .build();
        scheduleRepository.save(newSchedule);
    }

    private ParsedScheduleId parseScheduleId(String scheduleId) {
        if (scheduleId.contains("_")) {
            String[] parts = scheduleId.split("_");
            return new ParsedScheduleId(Integer.parseInt(parts[0]), LocalDate.parse(parts[1]), true);
        } else {
            return new ParsedScheduleId(Integer.parseInt(scheduleId), null, false);
        }
    }

    private record ParsedScheduleId(Integer dbId, LocalDate date, boolean isVirtual) {
        public Integer parentId() {
            if (!isVirtual)
                throw new IllegalStateException("Not a virtual ID");
            return dbId;
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 8 * * *")
    public void sendDailyScheduleAlarm() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        YearMonth ym = YearMonth.from(today);

        // 모든 가족 그룹 순회
        List<Family> families = familyRepository.findAll();

        for (Family family : families) {
            try {
                // 해당 가족의 오늘 일정 계산
                // calculateMonthlySchedules는 한 달 치를 계산하므로 비효율적일 수 있지만,
                // 로직 재사용 측면에서는 가장 확실함. (반복, 음력 등 처리)
                // 성능 최적화가 필요하다면 범위 조회를 줄여서 호출해야 함.
                List<ScheduleResponseDTO> monthlySchedules = calculateMonthlySchedules(
                        family.getId(), ym, null, null, null, null);

                // 오늘 날짜 일정만 필터링
                List<ScheduleResponseDTO> todaySchedules = monthlySchedules.stream()
                        .filter(s -> s.getStartAt().toLocalDate().equals(today))
                        .toList();

                if (todaySchedules.isEmpty()) {
                    continue;
                }

                // 알림 메시지 구성
                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder.append(String.format("오늘 총 %d개의 일정이 있습니다. ", todaySchedules.size()));

                List<Map<String, Object>> scheduleList = new ArrayList<>();
                for (ScheduleResponseDTO s : todaySchedules) {
                    contentBuilder.append(String.format("%s, %s. ",
                            s.getStartAt().toLocalTime().toString(), s.getTitle()));

                    Map<String, Object> sMap = new HashMap<>();
                    sMap.put("title", s.getTitle());
                    sMap.put("time", s.getStartAt().toLocalTime().toString());
                    scheduleList.add(sMap);
                }

                Map<String, Object> data = new HashMap<>();
                data.put("events_for_today", scheduleList);

                // IoT 기기로 전송
                List<org.ssafy.eeum.domain.iot.entity.IotDevice> devices = iotDeviceRepository
                        .findAllByFamilyId(family.getId());

                for (org.ssafy.eeum.domain.iot.entity.IotDevice device : devices) {
                    mqttService.sendAlarm(device.getSerialNumber(), "schedule", contentBuilder.toString(), data);
                }

            } catch (Exception e) {
                log.error("Failed to send daily schedule alarm for family {}: {}", family.getId(), e.getMessage());
            }
        }
    }
}