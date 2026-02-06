package org.ssafy.eeum.domain.medication.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.medication.dto.request.MedicationRequest;
import org.ssafy.eeum.domain.medication.dto.response.MedicationResponse;
import org.ssafy.eeum.domain.medication.service.MedicationService;

import java.util.List;

@Tag(name = "Medication", description = "복약 정보 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/families/{familyId}/medications")
public class MedicationController {

    private final MedicationService medicationService;

    @Operation(summary = "복약 정보 생성", description = "특정 가족의 복약 정보를 일괄 생성합니다.")
    @PostMapping
    public ResponseEntity<List<Long>> createMedicationPlans(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long familyId,
            @RequestBody List<MedicationRequest> requests) {
        List<Long> ids = medicationService.createMedicationPlans(userDetails.getId(), familyId, requests);
        return ResponseEntity.ok(ids);
    }

    @Operation(summary = "복약 정보 상세 조회", description = "ID로 복약 정보를 조회합니다.")
    @GetMapping("/{medicationId}")
    public ResponseEntity<MedicationResponse> getMedicationPlan(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long familyId,
            @PathVariable Long medicationId) {
        MedicationResponse response = medicationService.getMedicationPlan(userDetails.getId(), familyId, medicationId);
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "가족별 복약 정보 조회", description = "가족 ID로 해당 가족의 모든 복약 정보를 조회합니다.")
    @GetMapping
    public ResponseEntity<List<MedicationResponse>> getMedicationPlans(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long familyId) {
        List<MedicationResponse> responses = medicationService.getMedicationPlansByGroupId(userDetails.getId(), familyId);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "복약 정보 삭제", description = "ID로 복약 정보를 삭제합니다.")
    @DeleteMapping("/{medicationId}")
    public ResponseEntity<Void> deleteMedicationPlan(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long familyId,
            @PathVariable Long medicationId) {
        medicationService.deleteMedicationPlan(userDetails.getId(), familyId, medicationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "복약 정보 수정", description = "ID로 복약 정보를 수정합니다.")
    @PutMapping("/{medicationId}")
    public ResponseEntity<Void> updateMedicationPlan(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long familyId,
            @PathVariable Long medicationId,
            @RequestBody MedicationRequest request) {
        medicationService.updateMedicationPlan(userDetails.getId(), familyId, medicationId, request);
        return ResponseEntity.ok().build();
    }
}
