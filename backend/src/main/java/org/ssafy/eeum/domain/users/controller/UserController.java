package org.ssafy.eeum.domain.users.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.ssafy.eeum.domain.users.dto.ProfileRequestDto;
import org.ssafy.eeum.domain.users.dto.ProfileResponseDto;
import org.ssafy.eeum.domain.users.service.UserService;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name="users", description = "유저 프로필")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final org.ssafy.eeum.domain.family.service.FamilyService familyService;

    @Operation(summary = "유저 프로필 정보 수정", description = "유저의 프로필 정보를 수정합니다. 프로필 이미지도 변경 가능합니다.")
    @PutMapping(value = "/profile")
    public ResponseEntity<ProfileResponseDto> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("request") ProfileRequestDto profileRequest,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        String userId = userDetails.getUsername();
        ProfileResponseDto profileResponse = userService.updateProfile(userId, profileRequest, file);
        return ResponseEntity.ok(profileResponse);
    }
    
    @Operation(summary = "유저 프로필 정보 조회", description = "현재 인증된 유저의 프로필 정보를 조회합니다.")
    @GetMapping("/profile/me")
    public ResponseEntity<ProfileResponseDto> getProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        String userId = userDetails.getUsername();
        ProfileResponseDto profileResponse = userService.getProfile(userId);
        return ResponseEntity.ok(profileResponse);
    }

    @Operation(summary = "FCM 토큰 업데이트", description = "유저의 FCM 토큰을 업데이트합니다.")
    @PutMapping("/fcm-token")
    public ResponseEntity<Void> updateFcmToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @org.springframework.web.bind.annotation.RequestBody org.ssafy.eeum.domain.users.dto.FcmTokenRequestDto requestDto) {
        String userId = userDetails.getUsername();
        userService.updateFcmToken(userId, requestDto.getFcmToken());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "FCM 알림 테스트", description = "현재 로그인한 유저의 앱으로 테스트 알림을 발송합니다.")
    @org.springframework.web.bind.annotation.PostMapping("/fcm-test")
    public ResponseEntity<String> sendTestNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @org.springframework.web.bind.annotation.RequestBody(required = false) org.ssafy.eeum.domain.users.dto.FcmMessageRequestDto requestDto) {
        
        String userId;
        if (requestDto != null && requestDto.getTargetUserId() != null && !requestDto.getTargetUserId().isEmpty()) {
            userId = requestDto.getTargetUserId();
        } else {
            userId = userDetails.getUsername();
        }
        
        String title = requestDto != null ? requestDto.getTitle() : null;
        String body = requestDto != null ? requestDto.getBody() : null;
        String type = requestDto != null ? requestDto.getType() : null;

        String result = userService.sendTestMessage(userId, title, body, type);
        return ResponseEntity.ok(result);
    }
}

