package org.ssafy.eeum.domain.health.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.health.dto.HealthMetricRequestDTO;
import org.ssafy.eeum.domain.health.entity.HealthMetric;
import org.ssafy.eeum.domain.health.repository.HealthMetricRepository;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import org.ssafy.eeum.domain.family.entity.Family;
import org.ssafy.eeum.domain.family.repository.FamilyRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthService {

        private final HealthMetricRepository healthMetricRepository;
        private final FamilyRepository familyRepository;
        private final org.ssafy.eeum.domain.family.repository.SupporterRepository supporterRepository;

        @Transactional
        public void saveHealthMetrics(Integer groupId, List<HealthMetricRequestDTO> requests) {
                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "가족 그룹을 찾을 수 없습니다."));

                List<HealthMetric> metrics = requests.stream()
                                .map(dto -> dto.toEntity(family))
                                .toList();

                healthMetricRepository.saveAll(metrics);
        }

        public org.ssafy.eeum.domain.health.entity.HealthMetric getPatientLatestMetrics(Integer groupId) {
                Family family = familyRepository.findById(groupId)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "가족 그룹을 찾을 수 없습니다."));

                // Find the PATIENT in the group to verify its existence
                supporterRepository
                                .findByFamilyAndRole(family, org.ssafy.eeum.domain.family.entity.Supporter.Role.PATIENT)
                                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND,
                                                "그룹 내 피부양자를 찾을 수 없습니다."));

                // Get latest metric for this family
                return healthMetricRepository.findFirstByFamilyOrderByRecordDateDesc(family)
                                .orElse(null);
        }
}
