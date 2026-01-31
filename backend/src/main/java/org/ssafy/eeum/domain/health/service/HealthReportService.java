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

    @Transactional
    public HealthReportResponseDTO getDailyReport(Integer groupId, LocalDate date) {
        Family family = familyRepository.findById(groupId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND, "가족 그룹을 찾을 수 없습니다."));

        Optional<HealthReport> reportOpt = healthReportRepository.findByFamilyAndReportDateAndReportType(
                family, date, HealthReport.ReportType.DAILY);

        if (reportOpt.isPresent()) {
            return HealthReportResponseDTO.from(reportOpt.get());
        }

        // Create a new report if not exists (Mock summary for now based on metrics)
        HealthReport newReport = generateMockDailyReport(family, date);
        healthReportRepository.save(newReport);
        return HealthReportResponseDTO.from(newReport);
    }

    private HealthReport generateMockDailyReport(Family family, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(23, 59, 59);
        List<HealthMetric> metrics = healthMetricRepository.findByFamilyAndRecordDateBetween(family, start, end);

        String summary;
        String description;

        if (metrics.isEmpty()) {
            summary = "오늘 수집된 건강 데이터가 없습니다.";
            description = "사용자의 활동이나 생체 신호 데이터가 기록되지 않았습니다. 워치 연결 상태를 확인해주세요.";
        } else {
            // Simple mock logic for demonstration
            int totalSteps = metrics.stream().mapToInt(m -> m.getSteps() != null ? m.getSteps() : 0).sum();
            int avgHeartRate = (int) metrics.stream()
                    .filter(m -> m.getAverageHeartRate() != null)
                    .mapToInt(HealthMetric::getAverageHeartRate)
                    .average().orElse(0);

            summary = String.format("오늘은 총 %d걸음을 걸으셨으며, 평균 심박수는 %dBPM으로 건강한 상태입니다.", totalSteps, avgHeartRate);
            description = String.format("상세 분석 결과, 활동량은 %s 수준이며 심박수 변동성은 정상 범위 내에 있습니다.",
                    totalSteps > 5000 ? "양호한" : "보통");
        }

        return HealthReport.builder()
                .family(family)
                .reportDate(date)
                .reportType(HealthReport.ReportType.DAILY)
                .summary(summary)
                .description(description)
                .build();
    }
}
