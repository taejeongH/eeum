package org.ssafy.eeum.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ssafy.eeum.domain.auth.dto.request.LoginRequest;
import org.ssafy.eeum.domain.auth.dto.request.SignupRequest;
import org.ssafy.eeum.domain.auth.service.AuthService;
import org.ssafy.eeum.global.auth.model.TokenDto;

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

    @Operation(summary = "인증 코드 전송", description = "입력한 이메일로 인증 코드를 전송합니다.")
    @PostMapping("/email/code")
    public ResponseEntity<String> sendCode(@Valid @RequestBody org.ssafy.eeum.domain.auth.dto.request.EmailRequest request) {
        authService.sendCode(request.getEmail());
        return ResponseEntity.ok("인증 코드가 전송되었습니다.");
    }

    @Operation(summary = "인증 코드 검증", description = "이메일과 인증 코드를 검증합니다.")
    @PostMapping("/email/verify")
    public ResponseEntity<String> verifyCode(@Valid @RequestBody org.ssafy.eeum.domain.auth.dto.request.VerificationCodeRequest request) {
        authService.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok("이메일 인증이 완료되었습니다.");
    }

    @Operation(summary = "CustomUserDetails 확인", description = "토큰을 통해 해석된 CustomUserDetails 정보를 반환합니다.")
    @GetMapping("/test")
    public ResponseEntity<java.util.Map<String, Object>> testCustomUserDetails(
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.ssafy.eeum.global.auth.model.CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Unauthorized: Token missing or invalid"));
        }

        java.util.Map<String, Object> userInfo = new java.util.HashMap<>();
        userInfo.put("id", userDetails.getId());
        userInfo.put("email", userDetails.getEmail());
        userInfo.put("role", userDetails.getRole());
        return ResponseEntity.ok(userInfo);
    }
}
