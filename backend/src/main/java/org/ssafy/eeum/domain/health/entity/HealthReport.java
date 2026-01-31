package org.ssafy.eeum.domain.health.entity;

import jakarta.persistence.*;
import lombok.*;
import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.global.common.model.BaseEntity;

import java.time.LocalDate;

@Entity
@Table(name = "health_reports", uniqueConstraints = {
        @UniqueConstraint(name = "idx_group_date_type", columnNames = { "group_id", "report_date", "report_type" })
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class HealthReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Family family;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType;

    public enum ReportType {
        DAILY, WEEKLY
    }
}
