package org.ssafy.eeum.global.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Tag(name = "Auth Test", description = "인증 테스트 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @Operation(summary = "CustomUserDetails 확인", description = "토큰을 통해 해석된 CustomUserDetails 정보를 반환합니다.")
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testCustomUserDetails(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            log.warn("Test API Request - Anonymous User");
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized: Token missing or invalid"));
        }

        log.info("Test API Request - UserID: {}", userDetails.getId());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", userDetails.getId());
        userInfo.put("email", userDetails.getEmail());
        userInfo.put("role", userDetails.getRole());
        return ResponseEntity.ok(userInfo);
    }
}
