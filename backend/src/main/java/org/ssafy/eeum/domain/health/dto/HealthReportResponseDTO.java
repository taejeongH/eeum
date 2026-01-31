package org.ssafy.eeum.domain.health.dto;

import lombok.*;
import org.ssafy.eeum.domain.health.entity.HealthReport;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthReportResponseDTO {
    private Integer id;
    private Integer groupId;
    private LocalDate reportDate;
    private String summary;
    private String description;
    private String reportType;

    public static HealthReportResponseDTO from(HealthReport report) {
        return HealthReportResponseDTO.builder()
                .id(report.getId())
                .groupId(report.getFamily().getId())
                .reportDate(report.getReportDate())
                .summary(report.getSummary())
                .description(report.getDescription())
                .reportType(report.getReportType().name())
                .build();
    }
}
