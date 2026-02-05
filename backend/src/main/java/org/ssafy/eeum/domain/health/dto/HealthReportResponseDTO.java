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
    private Object description;
    private String reportType;

    public static HealthReportResponseDTO from(HealthReport report) {
        Object descriptionParsed = null;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            descriptionParsed = mapper.readValue(report.getDescription(), Object.class);
        } catch (Exception e) {
            descriptionParsed = report.getDescription(); // Fallback to raw string
        }

        return HealthReportResponseDTO.builder()
                .id(report.getId())
                .groupId(report.getFamily().getId())
                .reportDate(report.getReportDate())
                .summary(report.getSummary())
                .description(descriptionParsed)
                .reportType(report.getReportType().name())
                .build();
    }
}
