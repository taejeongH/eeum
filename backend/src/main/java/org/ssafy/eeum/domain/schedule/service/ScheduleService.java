package org.ssafy.eeum.domain.schedule.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.iot.entity.IotDevice;
import org.ssafy.eeum.domain.iot.repository.IotDeviceRepository;
import org.ssafy.eeum.domain.iot.service.IotSyncService;
import org.ssafy.eeum.domain.schedule.dto.ScheduleRequestDTO;
import org.ssafy.eeum.domain.schedule.dto.ScheduleResponseDTO;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.domain.schedule.entity.CategoryType;
import org.ssafy.eeum.domain.schedule.entity.RepeatType;
import org.ssafy.eeum.domain.schedule.entity.Schedule;
import org.ssafy.eeum.domain.schedule.repository.ScheduleRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.mqtt.MqttService;
import org.ssafy.eeum.global.infra.redis.RedisService;
import org.ssafy.eeum.global.util.CalendarUtils;
import org.ssafy.eeum.domain.family.repository.SupporterRepository;

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

/**
 * 가족 일정과 관련된 비즈니스 로직을 처리하는 서비스 클래스입니다.
 * 일정의 조회, 등록, 수정, 삭제 및 반복 일정 계산, 알림 전송 등을 담당합니다.
 * 
 * @summary 가족 일정 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final RedisService redisService;
    private final IotSyncService iotSyncService;
    private final MqttService mqttService;
    private final IotDeviceRepository iotDeviceRepository;
    private final SupporterRepository supporterRepository;

    /**
     * 사용자가 해당 가족 그룹의 구성원인지 권한을 확인합니다.
     * 
     * @summary 가족 그룹 접근 권한 확인
     * @param userId   사용자 식별자
     * @param familyId 가족 그룹 식별자
     * @throws CustomException 권한이 없거나 엔티티가 없을 경우 발생
     */
    private void checkFamilyAccess(Integer userId, Integer familyId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAMILY_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        supporterRepository.findByUserAndFamily(user, family)
                .orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN_FAMILY_ACCESS));
    }

    /**
     * 특정 월의 모든 가족 일정을 조회합니다.
     * 캐시 및 다양한 필터링(카테고리, 키워드 등)을 지원합니다.
     * 
     * @summary 월간 일정 목록 조회
     * @param userId       요청자 식별자
     * @param familyId     가족 그룹 식별자
     * @param year         조회 연도
     * @param month        조회 월
     * @param category     필터링할 카테고리 (선택)
     * @param keyword      검색어 (선택)
     * @param targetPerson 대상자 이름 (선택)
     * @param isVisited    방문 여부 필터 (선택)
     * @return 필터링된 일정 응답 DTO 리스트
     */
    public List<ScheduleResponseDTO> getMonthlySchedules(Integer userId, Integer familyId, int year, int month,
            String category,
            String keyword, String targetPerson, Boolean isVisited) {

        checkFamilyAccess(userId, familyId);

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

    /**
     * 단일 일정의 상세 정보를 조회합니다.
     * 가상 ID를 분석하여 원본 또는 개별 발생 일정을 반환합니다.
     * 
     * @summary 일정 상세 정보 조회
     * @param userId     요청자 식별자
     * @param familyId   가족 그룹 식별자
     * @param scheduleId 일정 식별자 (DB ID 또는 가상 ID)
     * @return 일정 상세 정보 DTO
     */
    public ScheduleResponseDTO getSchedule(Integer userId, Integer familyId, String scheduleId) {
        checkFamilyAccess(userId, familyId);
        if (scheduleId.contains("_")) {
            String[] parts = scheduleId.split("_");
            Integer parentId = Integer.parseInt(parts[0]);
            LocalDate targetDate = LocalDate.parse(parts[1]);

            Optional<Schedule> exceptionSchedule = scheduleRepository.findAll().stream()
                    .filter(s -> s.getParentId() != null && s.getParentId().equals(parentId)
                            && s.getStartAt() != null && s.getStartAt().toLocalDate().equals(targetDate))
                    .findFirst();

            if (exceptionSchedule.isPresent()) {
                return convertToResponse(exceptionSchedule.get(), scheduleId, targetDate, true);
            }

            Schedule parent = scheduleRepository.findById(parentId)
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

            List<LocalDate> occurrences = calculateOccurrenceDates(parent, targetDate, targetDate);
            if (occurrences.isEmpty()) {
                throw new CustomException(ErrorCode.ENTITY_NOT_FOUND);
            }

            return convertToResponse(parent, scheduleId, targetDate, false);

        } else {
            Schedule schedule = scheduleRepository.findById(Integer.parseInt(scheduleId))
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

            return convertToResponse(schedule, scheduleId, schedule.getStartAt().toLocalDate(), false);
        }
    }

    private List<ScheduleResponseDTO> calculateMonthlySchedules(Integer familyId, YearMonth ym, String category,
            String keyword, String targetPerson, Boolean isVisited) {
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        List<Schedule> candidates = scheduleRepository.findCandidates(familyId, startDateTime, endDateTime);

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
                            return !visitFilter;
                        return s.getIsVisited() == visitFilter;
                    })
                    .toList();
        }

        Set<String> modifiedMask = scheduleRepository.findCandidates(familyId, startDateTime, endDateTime).stream()
                .filter(s -> s.getParentId() != null)
                .map(s -> s.getParentId() + "_" + s.getStartAt().toLocalDate())
                .collect(Collectors.toSet());

        List<ScheduleResponseDTO> result = new ArrayList<>();

        for (Schedule s : candidates) {
            if ("EXCLUDED".equals(s.getTitle())) {
                continue;
            }

            List<LocalDate> occurrences = calculateOccurrenceDates(s, start, end);

            for (LocalDate date : occurrences) {
                String virtualId = (s.getRepeatType() == RepeatType.NONE) ? s.getId().toString()
                        : s.getId() + "_" + date;

                if (s.getRepeatType() != RepeatType.NONE && modifiedMask.contains(s.getId() + "_" + date)) {
                    continue;
                }

                result.add(convertToResponse(s, virtualId, date, s.getParentId() != null));
            }
        }
        return result.stream().sorted(Comparator.comparing(ScheduleResponseDTO::getStartAt)).toList();
    }

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
                targetDate = CalendarUtils.convertLunarToSolar(baseDate.withYear(start.getYear()));
            } else {
                targetDate = baseDate.withYear(start.getYear());
            }
        }

        // 반복 없음
        if (s.getRepeatType() == RepeatType.NONE) {
            if (!targetDate.isBefore(start) && !targetDate.isAfter(end)) {
                dates.add(targetDate);
            }
        }

        // 매년 반복 (YEARLY)
        else if (s.getRepeatType() == RepeatType.YEARLY) {
            if (isValidOccurrence(targetDate, start, end, baseDate, limitDate))
                dates.add(targetDate);

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
                .creatorId(s.getCreator().getId())
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

    /**
     * 새로운 일정을 등록합니다.
     * 시작 시간이 종료 시간보다 늦을 수 없으며, 예약된 일관된 필터 키워드는 제목으로 사용할 수 없습니다.
     * 
     * @summary 신규 일정 등록
     * @param userId   생성자 식별자
     * @param familyId 가족 그룹 식별자
     * @param dto      일정 등록 정보 DTO
     */
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

    /**
     * 기존 일정을 수정합니다.
     * 반복 일정의 특정 발생(가상 ID)을 수정할 경우, 예외 일정을 생성하여 처리합니다.
     * 
     * @summary 일정 정보 수정
     * @param userId     수정 요청자 식별자
     * @param familyId   가족 그룹 식별자
     * @param scheduleId 일정 식별자 (DB ID 또는 가상 ID)
     * @param dto        일정 수정 정보 DTO
     */
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

                        if (!parent.getCreator().getId().equals(userId)) {
                            throw new CustomException(ErrorCode.NOT_SCHEDULE_CREATOR);
                        }

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

            if (!schedule.getCreator().getId().equals(userId)) {
                throw new CustomException(ErrorCode.NOT_SCHEDULE_CREATOR);
            }

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

    /**
     * 일정을 삭제합니다.
     * 단일 삭제 또는 반복 일정 전체 삭제 옵션을 제공합니다.
     * 
     * @summary 일정 삭제 처리
     * @param userId     삭제 요청자 식별자
     * @param familyId   가족 그룹 식별자
     * @param scheduleId 일정 식별자
     * @param deleteAll  반복 일정 전체 삭제 여부
     */
    @Transactional
    public void deleteSchedule(Integer userId, Integer familyId, String scheduleId, boolean deleteAll) {
        ParsedScheduleId parsedId = parseScheduleId(scheduleId);

        if (deleteAll) {
            Integer targetId = parsedId.isVirtual() ? parsedId.parentId() : parsedId.dbId();

            Schedule schedule = scheduleRepository.findById(targetId)
                    .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

            if (!schedule.getCreator().getId().equals(userId)) {
                throw new CustomException(ErrorCode.NOT_SCHEDULE_CREATOR);
            }

            scheduleRepository.delete(schedule);
            invalidateCache(familyId, schedule.getStartAt().toLocalDate());
        } else {
            if (parsedId.isVirtual()) {
                Integer parentId = parsedId.parentId();
                LocalDate targetDate = parsedId.date();

                Schedule parent = scheduleRepository.findById(parentId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                if (!parent.getCreator().getId().equals(userId)) {
                    throw new CustomException(ErrorCode.NOT_SCHEDULE_CREATOR);
                }

                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "사용자를 찾을 수 없습니다."));

                createAndSaveExclusion(user, familyId, parent, targetDate);
                invalidateCache(familyId, targetDate);
            } else {
                Schedule schedule = scheduleRepository.findById(parsedId.dbId())
                        .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

                if (!schedule.getCreator().getId().equals(userId)) {
                    throw new CustomException(ErrorCode.NOT_SCHEDULE_CREATOR);
                }

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

    /**
     * 일정의 방문 여부 상태를 변경합니다.
     * 반복 일정의 일부일 경우 독립된 일정으로 분리하여 상태를 기록합니다.
     * 
     * @summary 방문 상태 업데이트
     * @param userId     요청자 식별자
     * @param familyId   가족 그룹 식별자
     * @param scheduleId 일정 식별자
     * @param visited    방문 완료 여부
     */
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

    /**
     * 매일 오전 8시에 오늘의 일정을 조회하여 가족 구성원들에게 알림을 보냅니다.
     * 
     * @summary 당일 일정 모닝 알람 전송
     */
    @Transactional
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyScheduleAlarm() {
        sendScheduleAlarmForDate(LocalDate.now(), "오늘");
    }

    /**
     * 매일 오후 8시에 내일의 일정을 미리 알림으로 전송합니다.
     * 
     * @summary 익일 일정 사전 알람 전송
     */
    @Transactional
    @Scheduled(cron = "0 0 20 * * *")
    public void sendNextDayScheduleAlarm() {
        sendScheduleAlarmForDate(LocalDate.now().plusDays(1), "내일");
    }

    private void sendScheduleAlarmForDate(LocalDate targetDate, String dayLabel) {
        YearMonth ym = YearMonth.from(targetDate);

        List<Family> families = familyRepository.findAll();

        for (Family family : families) {
            try {
                List<ScheduleResponseDTO> monthlySchedules = calculateMonthlySchedules(
                        family.getId(), ym, null, null, null, null);

                List<ScheduleResponseDTO> targetSchedules = monthlySchedules.stream()
                        .filter(s -> s.getStartAt().toLocalDate().equals(targetDate))
                        .toList();

                StringBuilder contentBuilder = new StringBuilder();
                List<Map<String, Object>> scheduleList = new ArrayList<>();

                if (targetSchedules.isEmpty()) {
                    contentBuilder.append(String.format("%s 예정된 일정이 없습니다.", dayLabel));
                } else {
                    contentBuilder.append(String.format("%s 총 %d개의 일정이 있습니다. ", dayLabel, targetSchedules.size()));

                    for (ScheduleResponseDTO s : targetSchedules) {
                        contentBuilder.append(String.format("%s, %s. ",
                                s.getStartAt().toLocalTime().toString(), s.getTitle()));

                        Map<String, Object> sMap = new HashMap<>();
                        sMap.put("title", s.getTitle());
                        sMap.put("time", s.getStartAt().toLocalTime().toString());
                        scheduleList.add(sMap);
                    }
                }

                Map<String, Object> data = new HashMap<>();
                data.put("events_for_today", scheduleList);

                List<IotDevice> devices = iotDeviceRepository.findAllByFamilyId(family.getId());

                for (IotDevice device : devices) {
                    mqttService.sendAlarm(device.getSerialNumber(), "schedule", contentBuilder.toString(), data);
                }
            } catch (Exception e) {
                log.error("Failed to send {} schedule alarm for family {}: {}", dayLabel, family.getId(),
                        e.getMessage());
            }
        }
    }

    /**
     * 사용자의 생일 정보를 바탕으로 반복되는 생일 일정을 등록합니다.
     * 앱 내 가족 통계나 알림에서 사용됩니다.
     * 
     * @summary 생일 일정 자동 등록
     * @param user   생일 대상 사용자
     * @param family 대상 가족 그룹
     */
    @Transactional
    public void addBirthdaySchedule(User user, Family family) {
        if (user.getBirthDate() == null) {
            return;
        }

        String title = user.getName() + "님의 생신";
        boolean exists = scheduleRepository.findAll().stream()
                .anyMatch(s -> s.getFamily().getId().equals(family.getId()) &&
                        s.getTitle().equals(title) &&
                        s.getCategoryType() == CategoryType.BIRTHDAY);

        if (exists) {
            return;
        }

        LocalDateTime birthDateTime = user.getBirthDate().atTime(9, 0);
        Schedule schedule = Schedule.builder()
                .family(family)
                .creator(family.getUser())
                .title(title)
                .startAt(birthDateTime)
                .endAt(birthDateTime.plusHours(1))
                .categoryType(CategoryType.BIRTHDAY)
                .description(user.getName() + "님의 생일")
                .repeatType(RepeatType.YEARLY)
                .isLunar(false)
                .targetPerson(user.getName())
                .isVisited(false)
                .build();

        scheduleRepository.save(schedule);

        // Robust Cache Invalidation: Clear cache for the birth month in multiple years
        // to ensure visibility in the current calendar view period.
        int currentYear = LocalDate.now().getYear();
        invalidateCache(family.getId(), user.getBirthDate().withYear(currentYear));
        invalidateCache(family.getId(), user.getBirthDate().withYear(currentYear + 1));
        invalidateCache(family.getId(), user.getBirthDate()); // Clear original birth year month too

        iotSyncService.notifyUpdate(family.getId(), "schedule");
    }
}
