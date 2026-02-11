package org.ssafy.eeum.domain.family.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.family.dto.*;
import org.ssafy.eeum.domain.family.service.FamilyService;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;

import java.util.List;

/**
 * 가족 그룹 및 멤버 관리를 위한 API를 제공하는 컨트롤러 클래스입니다.
 * 가족 생성, 가입, 상세 정보 조회, 멤버 관리 등의 기능을 포함합니다.
 * 
 * @summary 가족 관리 컨트롤러
 */
@Tag(name = "Family", description = "가족 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/families")
public class FamilyController {

    private final FamilyService familyService;

    /**
     * 초대 코드를 사용하여 참여할 가족 그룹에 대한 미리보기 정보를 조회합니다.
     * 
     * @summary 가족 그룹 참여 미리보기
     * @param code 초대 코드
     * @return 가족 참여 미리보기 정보 DTO
     */
    @Operation(summary = "가족 그룹 참여 미리보기", description = "초대 코드를 사용하여 가족 그룹 참여 정보를 미리 봅니다.")
    @GetMapping("/join/preview")
    public ResponseEntity<JoinPreviewResponseDto> getFamilyJoinPreview(
            @RequestParam String code) {
        JoinPreviewResponseDto responseDto = familyService.getFamilyJoinPreview(code);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 새로운 가족 그룹을 생성합니다. 생성자는 자동으로 가족 대표자가 됩니다.
     * 
     * @summary 가족 그룹 생성
     * @param userDetails            현재 로그인한 사용자 정보
     * @param createFamilyRequestDto 가족 생성 정보 DTO
     * @return 생성된 가족 정보 DTO
     */
    @Operation(summary = "가족 그룹 생성", description = "새로운 가족 그룹을 생성합니다.")
    @PostMapping
    public ResponseEntity<CreateFamilyResponseDto> createFamily(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CreateFamilyRequestDto createFamilyRequestDto) {
        String userId = userDetails.getUsername();
        CreateFamilyResponseDto responseDto = familyService.createFamily(userId, createFamilyRequestDto);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 현재 사용자가 속해 있는 모든 가족 그룹의 목록을 조회합니다.
     * 
     * @summary 소속 가족 목록 조회
     * @param userDetails 현재 로그인한 사용자 정보
     * @return 가족 목록 DTO 리스트
     */
    @Operation(summary = "가족 목록 조회", description = "현재 유저가 속한 모든 가족 그룹의 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<FamilySimpleResponseDto>> getMyFamilies(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String userId = userDetails.getUsername();
        List<FamilySimpleResponseDto> responseDto = familyService.findMyFamilies(userId);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 특정 가족 그룹의 상세 정보(이름, 구성원 명수 등)를 조회합니다.
     * 
     * @summary 가족 그룹 상세 정보 조회
     * @param familyId 가족 그룹 식별자
     * @return 가족 상세 정보 DTO
     */
    @Operation(summary = "가족 그룹 상세 정보 조회", description = "특정 가족 그룹의 이름, 피부양자, 멤버, 우선순위 등 상세 정보를 조회합니다.")
    @GetMapping("/{familyId}/details")
    public ResponseEntity<FamilyDetailResponseDto> getFamilyDetails(
            @PathVariable Integer familyId) {
        FamilyDetailResponseDto responseDto = familyService.getFamilyDetails(familyId);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 가족 그룹에 속한 모든 멤버의 정보를 요약 조회합니다.
     * 
     * @summary 가족 멤버 목록 조회
     * @param userDetails 현재 로그인한 사용자 정보
     * @param familyId    가족 그룹 식별자
     * @return 멤버 정보 DTO 리스트
     */
    @Operation(summary = "가족 그룹 멤버 목록 조회", description = "특정 가족 그룹에 속한 모든 멤버의 목록을 조회합니다.")
    @GetMapping("/{familyId}/members")
    public ResponseEntity<List<FamilyMemberDto>> getFamilyMembers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Integer familyId) {
        String userId = userDetails.getUsername();
        List<FamilyMemberDto> responseDto = familyService.getFamilyMembers(userId, familyId);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 특정 가족 멤버의 상세 프로필 정보를 조회합니다.
     * 
     * @summary 가족 멤버 상세 조회
     * @param userDetails  현재 로그인한 사용자 정보
     * @param familyId     가족 그룹 식별자
     * @param memberUserId 상세 조회할 멤버의 사용자 식별자
     * @return 멤버 상세 정보 DTO
     */
    @Operation(summary = "가족 그룹 멤버 상세 조회", description = "특정 가족 그룹 멤버의 상세 정보를 조회합니다.")
    @GetMapping("/{familyId}/members/{memberUserId}")
    public ResponseEntity<FamilyMemberDetailResponseDto> getFamilyMemberDetails(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Integer familyId,
            @PathVariable Integer memberUserId) {
        String userId = userDetails.getUsername();
        FamilyMemberDetailResponseDto responseDto = familyService.getFamilyMemberDetails(userId, familyId,
                memberUserId);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 가족 그룹에 새로운 멤버를 초대하기 위한 코드를 조회합니다.
     * 
     * @summary 초대 코드 조회
     * @param userDetails 현재 로그인한 사용자 정보
     * @param familyId    가족 그룹 식별자
     * @return 초대 코드 문자열
     */
    @Operation(summary = "가족 그룹 초대 코드 조회", description = "가족 대표자가 가족 그룹의 초대 코드를 조회합니다.")
    @GetMapping("/{familyId}/invite")
    public ResponseEntity<String> getInviteCode(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Integer familyId) {
        String userId = userDetails.getUsername();
        String inviteCode = familyService.getInviteCode(userId, familyId);
        return ResponseEntity.ok(inviteCode);
    }

    /**
     * 기존의 초대 코드를 폐기하고 새로운 초대 코드를 생성합니다.
     * 
     * @summary 초대 코드 재발급
     * @param userDetails 현재 로그인한 사용자 정보
     * @param familyId    가족 그룹 식별자
     * @return 새로 생성된 초대 코드 문자열
     */
    @Operation(summary = "가족 그룹 초대 코드 재발급", description = "가족 대표자가 가족 그룹의 초대 코드를 재발급합니다.")
    @PutMapping("/{familyId}/invite")
    public ResponseEntity<String> regenerateInviteCode(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Integer familyId) {
        String userId = userDetails.getUsername();
        String newInviteCode = familyService.regenerateInviteCode(userId, familyId);
        return ResponseEntity.ok(newInviteCode);
    }

    /**
     * 초대 코드를 입력하여 해당 가족 그룹에 멤버로 참여합니다.
     * 
     * @summary 가족 그룹 가입
     * @param userDetails 현재 로그인한 사용자 정보
     * @param inviteCode  초대 코드
     * @return 가입 완료된 가족 정보 DTO
     */
    @Operation(summary = "가족 그룹 참여", description = "초대 코드를 사용하여 가족 그룹에 참여합니다.")
    @PostMapping("/join")
    public ResponseEntity<FamilySimpleResponseDto> joinFamily(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody String inviteCode) {
        String userId = userDetails.getUsername();
        FamilySimpleResponseDto responseDto = familyService.joinFamily(userId, inviteCode);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 사용자가 가족 그룹에서 탈퇴합니다. 대표자가 탈퇴할 경우 그룹 자체가 삭제됩니다.
     * 
     * @summary 가족 그룹 탈퇴 및 삭제
     * @param userDetails 현재 로그인한 사용자 정보
     * @param familyId    탈퇴/삭제할 가족 그룹 식별자
     * @return 탈퇴 결과 정보 DTO
     */
    @Operation(summary = "가족 그룹 탈퇴/삭제", description = "가족 그룹에서 탈퇴하거나, 대표자일 경우 그룹을 삭제합니다.")
    @DeleteMapping("/{familyId}/leave")
    public ResponseEntity<LeaveFamilyResponseDto> leaveFamily(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Integer familyId) {
        String userId = userDetails.getUsername();
        LeaveFamilyResponseDto responseDto = familyService.leaveFamily(userId, familyId);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 가족 그룹의 이름, 피부양자 정보, 응급 우선순위 등의 설정을 수정합니다.
     * 
     * @summary 가족 그룹 설정 수정
     * @param userDetails 현재 로그인한 사용자 정보
     * @param familyId    가족 그룹 식별자
     * @param requestDto  수정할 정보 DTO
     * @return 수정 완료된 가족 정보 DTO
     */
    @Operation(summary = "가족 그룹 설정 수정", description = "가족 그룹의 이름, 피부양자 설정, 피부양자 건강 정보, 멤버 응급 우선순위를 수정합니다.")
    @PutMapping("/{familyId}")
    public ResponseEntity<UpdateFamilyResponseDto> updateFamily(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Integer familyId,
            @RequestBody UpdateFamilyRequestDto requestDto) {
        String userId = userDetails.getUsername();
        UpdateFamilyResponseDto responseDto = familyService.updateFamily(userId, familyId, requestDto);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 가족 대표자가 특정 멤버를 가족 그룹에서 강제 제외합니다.
     * 
     * @summary 가족 멤버 삭제
     * @param userDetails  현재 로그인한 사용자 정보
     * @param familyId     가족 그룹 식별자
     * @param memberUserId 제외할 멤버의 사용자 식별자
     */
    @Operation(summary = "가족 그룹 멤버 삭제", description = "가족 대표자가 다른 멤버를 그룹에서 삭제합니다.")
    @DeleteMapping("/{familyId}/members/{memberUserId}")
    public ResponseEntity<Void> deleteFamilyMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Integer familyId,
            @PathVariable Integer memberUserId) {
        String authenticatedUserId = userDetails.getUsername();
        familyService.deleteFamilyMember(authenticatedUserId, familyId, memberUserId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 현재 사용자의 가족 내 관계 호칭(예: 아들, 딸 등)을 수정합니다.
     * 
     * @summary 가족 내 관계 호칭 수정
     * @param userDetails 현재 로그인한 사용자 정보
     * @param familyId    가족 그룹 식별자
     * @param requestDto  수정할 관계 정보 DTO
     */
    @Operation(summary = "멤버 관계 수정", description = "현재 유저가 가족 대표자와의 관계를 수정합니다.")
    @PutMapping("/{familyId}/members/me/relationship")
    public ResponseEntity<Void> updateMyRelationship(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Integer familyId,
            @RequestBody UpdateMemberRelationshipRequestDto requestDto) {
        String authenticatedUserId = userDetails.getUsername();
        familyService.updateMyRelationship(authenticatedUserId, familyId, requestDto);
        return ResponseEntity.noContent().build();
    }
}
