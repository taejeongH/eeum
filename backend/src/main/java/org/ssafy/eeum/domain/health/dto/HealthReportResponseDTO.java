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
    private Object summary;
    private Object description;
    private String reportType;

    public static HealthReportResponseDTO from(HealthReport report) {
        Object summaryParsed = null;
        Object descriptionParsed = null;
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        try {
            summaryParsed = mapper.readValue(report.getSummary(), Object.class);
        } catch (Exception e) {
            summaryParsed = report.getSummary();
        }

        try {
            descriptionParsed = mapper.readValue(report.getDescription(), Object.class);
        } catch (Exception e) {
            descriptionParsed = report.getDescription();
        }

        return HealthReportResponseDTO.builder()
                .id(report.getId())
                .groupId(report.getFamily().getId())
                .reportDate(report.getReportDate())
                .summary(summaryParsed)
                .description(descriptionParsed)
                .reportType(report.getReportType().name())
                .build();
    }
}
