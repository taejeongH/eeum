package org.ssafy.eeum.domain.health.repository;

import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.health.entity.HealthReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface HealthReportRepository extends JpaRepository<HealthReport, Integer> {
    Optional<HealthReport> findByFamilyAndReportDateAndReportType(
            Family family, LocalDate reportDate, HealthReport.ReportType reportType);
}
