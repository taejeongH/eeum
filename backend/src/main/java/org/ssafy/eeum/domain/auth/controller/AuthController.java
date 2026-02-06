package org.ssafy.eeum.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.auth.dto.request.*;
import org.ssafy.eeum.domain.auth.service.AuthService;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.ssafy.eeum.global.auth.model.TokenDto;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "Auth", description = "인증 관련 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 이름을 입력받아 회원가입을 진행합니다. 이메일 인증이 선행되어야 합니다.")
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다. 이메일 인증이 완료되어야 합니다.")
    @PostMapping("/login")
    public ResponseEntity<TokenDto> login(@Valid @RequestBody LoginRequest request) {
        TokenDto tokenDto = authService.login(request);
        return ResponseEntity.ok(tokenDto);
    }

    @Operation(summary = "로그아웃", description = "로그아웃을 진행하고 서버에서 리프레시 토큰을 삭제합니다.")
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null) {
            authService.logout(userDetails.getEmail());
        }
        return ResponseEntity.ok("로그아웃 되었습니다.");
    }

    @Operation(summary = "인증 코드 전송", description = "입력한 이메일로 인증 코드를 전송합니다.")
    @PostMapping("/email/code")
    public ResponseEntity<String> sendCode(@Valid @RequestBody EmailRequest request) {
        authService.sendCode(request.getEmail());
        return ResponseEntity.ok("인증 코드가 전송되었습니다.");
    }

    @Operation(summary = "인증 코드 검증", description = "이메일과 인증 코드를 검증합니다.")
    @PostMapping("/email/verify")
    public ResponseEntity<String> verifyCode(@Valid @RequestBody VerificationCodeRequest request) {
        authService.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok("이메일 인증이 완료되었습니다.");
    }

    @Operation(summary = "이메일 찾기", description = "이름과 휴대폰 번호로 가입된 이메일을 찾습니다.")
    @PostMapping("/find/email")
    public ResponseEntity<String> findEmail(@Valid @RequestBody FindEmailRequest request) {
        String email = authService.findEmail(request.getName(), request.getPhone());
        return ResponseEntity.ok(email);
    }

    @Operation(summary = "비밀번호 재설정 인증 코드 전송", description = "비밀번호 재설정을 위해 인증 코드를 전송합니다.")
    @PostMapping("/password/code")
    public ResponseEntity<String> sendPasswordResetCode(@Valid @RequestBody EmailRequest request) {
        authService.sendPasswordResetCode(request.getEmail());
        return ResponseEntity.ok("인증 코드가 전송되었습니다.");
    }

    @Operation(summary = "비밀번호 재설정 인증 코드 검증", description = "비밀번호 재설정용 인증 코드를 검증합니다.")
    @PostMapping("/password/verify")
    public ResponseEntity<String> verifyPasswordResetCode(@Valid @RequestBody VerificationCodeRequest request) {
        authService.verifyPasswordResetCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok("인증이 완료되었습니다.");
    }

    @Operation(summary = "비밀번호 재설정", description = "새로운 비밀번호로 변경합니다. 인증이 선행되어야 합니다.")
    @PostMapping("/password/reset")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getEmail(), request.getNewPassword());
        return ResponseEntity.ok("비밀번호가 재설정되었습니다.");
    }

    @Operation(summary = "CustomUserDetails 확인", description = "토큰을 통해 해석된 CustomUserDetails 정보를 반환합니다.")
    @GetMapping("/test")
    public ResponseEntity<java.util.Map<String, Object>> testCustomUserDetails(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Unauthorized: Token missing or invalid"));
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", userDetails.getId());
        userInfo.put("email", userDetails.getEmail());
        userInfo.put("role", userDetails.getRole());
        return ResponseEntity.ok(userInfo);
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰을 사용하여 새로운 엑세스 토큰과 리프레시 토큰을 발급합니다.")
    @PostMapping("/reissue")
    public ResponseEntity<TokenDto> reissue(
            @Valid @RequestBody ReissueRequest request) {
        TokenDto tokenDto = authService.reissue(request.getRefreshToken());
        return ResponseEntity.ok(tokenDto);
    }
}
