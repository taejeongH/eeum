package org.ssafy.eeum.domain.health.service;

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthReportService {

        private final HealthReportRepository healthReportRepository;
        private final HealthMetricRepository healthMetricRepository;
        private final FamilyRepository familyRepository;
        private final org.ssafy.eeum.global.infra.gms.GmsService gmsService;

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

        private HealthReport generateReportWithAI(Family family, LocalDate date) {
                LocalDateTime start = date.atStartOfDay();
                LocalDateTime end = date.atTime(23, 59, 59);

                List<HealthMetric> metrics = healthMetricRepository.findByFamilyAndRecordDateBetween(family, start,
                                end);

                java.util.Map<String, Object> aiResult = gmsService.generateHealthReport(metrics);

                Object summaryObj = aiResult.get("summary");
                Object descriptionObj = aiResult.get("description");
                String summaryJson = "";
                String descriptionJson = "";

                try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        summaryJson = mapper.writeValueAsString(summaryObj);
                        descriptionJson = mapper.writeValueAsString(descriptionObj);
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
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
