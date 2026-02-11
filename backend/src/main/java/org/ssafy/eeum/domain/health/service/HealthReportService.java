package org.ssafy.eeum.domain.health.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;
import org.ssafy.eeum.domain.health.dto.HealthReportResponseDTO;
import org.ssafy.eeum.domain.health.entity.HealthMetric;
import org.ssafy.eeum.domain.health.entity.HealthReport;
import org.ssafy.eeum.domain.health.repository.HealthMetricRepository;
import org.ssafy.eeum.domain.health.repository.HealthReportRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import org.ssafy.eeum.global.infra.gms.GmsService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 사용자의 건강 지표를 분석하여 건강 리포트를 생성하고 관리하는 서비스 클래스입니다.
 * AI(GMS)를 연동하여 건강 지표 기반의 요약 및 상세 분석 결과를 생성합니다.
 * 
 * @summary 건강 리포트 관리 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthReportService {

        private final HealthReportRepository healthReportRepository;
        private final HealthMetricRepository healthMetricRepository;
        private final FamilyRepository familyRepository;
        private final GmsService gmsService;

        /**
         * 특정 날짜의 일간 건강 리포트를 조회합니다.
         * 만약 해당 날짜의 리포트가 존재하지 않으면 AI를 통해 새로 생성합니다.
         * 
         * @summary 일간 건강 리포트 조회 및 생성
         * @param groupId 가족 그룹 식별자
         * @param date    조회 대상 날짜
         * @return 건강 리포트 응답 DTO
         */
        @Transactional
        public HealthReportResponseDTO getDailyReport(Integer groupId, LocalDate date) {
                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "가족 그룹을 찾을 수 없습니다."));

                Optional<HealthReport> reportOpt = healthReportRepository.findByFamilyAndReportDateAndReportType(
                                family, date, HealthReport.ReportType.DAILY);

                if (reportOpt.isPresent()) {
                        return HealthReportResponseDTO.from(reportOpt.get());
                }

                HealthReport newReport = generateReportWithAI(family, date);
                HealthReport savedReport = healthReportRepository.save(newReport);
                return HealthReportResponseDTO.from(savedReport);
        }

        /**
         * 특정 날짜의 일간 건강 리포트를 재분석합니다.
         * 기존 리포트가 있다면 삭제하고 최신 건강 지표를 바탕으로 AI 분석을 다시 수행합니다.
         * 
         * @summary 건강 리포트 재분석 실행
         * @param groupId 가족 그룹 식별자
         * @param date    재분석 대상 날짜
         * @return 재분석된 건강 리포트 응답 DTO
         */
        @Transactional
        public HealthReportResponseDTO reanalyzeDailyReport(Integer groupId, LocalDate date) {
                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "가족 그룹을 찾을 수 없습니다."));

                Optional<HealthReport> existing = healthReportRepository.findByFamilyAndReportDateAndReportType(
                                family, date, HealthReport.ReportType.DAILY);

                HealthReport newReport = generateReportWithAI(family, date);

                if (existing.isPresent()) {
                        HealthReport report = existing.get();
                        healthReportRepository.delete(report);
                        healthReportRepository.flush();
                }

                HealthReport savedReport = healthReportRepository.save(newReport);
                return HealthReportResponseDTO.from(savedReport);
        }

        /**
         * 최신 건강 지표 데이터를 바탕으로 AI 분석 리포트를 생성합니다.
         * 
         * @summary AI 기반 건강 리포트 생성 로직
         * @param family 가족 그룹 상세
         * @param date   생성 날짜
         * @return 생성된 건강 리포트 엔티티
         */
        private HealthReport generateReportWithAI(Family family, LocalDate date) {
                LocalDateTime start = date.atStartOfDay();
                LocalDateTime end = date.atTime(23, 59, 59);

                List<HealthMetric> metrics = healthMetricRepository.findByFamilyAndRecordDateBetween(family, start,
                                end);

                Map<String, Object> aiResult = gmsService.generateHealthReport(metrics);

                Object summaryObj = aiResult.get("summary");
                Object descriptionObj = aiResult.get("description");
                String summaryJson = "";
                String descriptionJson = "";

                try {
                        ObjectMapper mapper = new ObjectMapper();
                        summaryJson = mapper.writeValueAsString(summaryObj);
                        descriptionJson = mapper.writeValueAsString(descriptionObj);
                } catch (JsonProcessingException e) {
                        summaryJson = "{\"text\":\"분석 중 오류가 발생했습니다.\",\"emoji\":\"⚠️\",\"score\":0}";
                        descriptionJson = "[]";
                }

                return HealthReport.builder()
                                .family(family)
                                .reportDate(date)
                                .reportType(HealthReport.ReportType.DAILY)
                                .summary(summaryJson)
                                .description(descriptionJson)
                                .build();
        }
}
