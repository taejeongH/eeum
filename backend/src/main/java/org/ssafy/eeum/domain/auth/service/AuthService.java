package org.ssafy.eeum.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.auth.dto.request.LoginRequest;
import org.ssafy.eeum.domain.auth.dto.request.SignupRequest;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.global.auth.jwt.JwtProvider;
import org.ssafy.eeum.global.auth.model.TokenDto;
import org.ssafy.eeum.global.email.service.EmailService;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtProvider jwtProvider;

    private static final String AUTH_CODE_PREFIX = "AuthCode:";
    private static final String AUTH_VERIFIED_PREFIX = "AuthVerified:";
    private static final String PW_RESET_CODE_PREFIX = "PWResetCode:";
    private static final String PW_RESET_VERIFIED_PREFIX = "PWResetVerified:";
    private static final String DEFAULT_PROFILE_IMAGE = "profile/taejeon_default_image.png";

    @Transactional
    public void sendCode(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        String code = generateRandomCode();
        redisTemplate.opsForValue().set(AUTH_CODE_PREFIX + email, code, 3, TimeUnit.MINUTES);
        emailService.sendVerificationCode(email, code);
    }

    @Transactional
    public void verifyCode(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get(AUTH_CODE_PREFIX + email);
        if (storedCode == null || !storedCode.equals(code)) {
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        redisTemplate.delete(AUTH_CODE_PREFIX + email);
        redisTemplate.opsForValue().set(AUTH_VERIFIED_PREFIX + email, "true", 30, TimeUnit.MINUTES);
    }

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        String isVerified = redisTemplate.opsForValue().get(AUTH_VERIFIED_PREFIX + request.getEmail());
        if (isVerified == null || !"true".equals(isVerified)) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .profileImage(DEFAULT_PROFILE_IMAGE)
                .isEmailVerified(true) // 이미 인증됨
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        redisTemplate.delete(AUTH_VERIFIED_PREFIX + request.getEmail());
    }

    private String generateRandomCode() {
        return String.valueOf((int) (Math.random() * 900000) + 100000);
    }

    @Transactional(readOnly = true)
    public String findEmail(String name, String phone) {
        // 이름과 휴대폰 번호로 사용자 찾기
        java.util.List<User> users = userRepository.findByNameAndPhone(name, phone);
        if (users.isEmpty()) {
            throw new CustomException(ErrorCode.ENTITY_NOT_FOUND);
        }

        // 첫 번째 사용자의 이메일 반환 (마스킹 처리)
        // 예: test@example.com -> te**@example.com
        String email = users.get(0).getEmail();
        return maskEmail(email);
    }

    @Transactional
    public void sendPasswordResetCode(String email) {
        // 가입된 이메일인지 확인
        if (userRepository.findByEmail(email).isEmpty()) {
            throw new CustomException(ErrorCode.ENTITY_NOT_FOUND);
        }

        String code = generateRandomCode();
        redisTemplate.opsForValue().set(PW_RESET_CODE_PREFIX + email, code, 3, TimeUnit.MINUTES);
        emailService.sendVerificationCode(email, code); // 기존 이메일 발송 메서드 재사용
    }

    @Transactional
    public void verifyPasswordResetCode(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get(PW_RESET_CODE_PREFIX + email);
        if (storedCode == null || !storedCode.equals(code)) {
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        redisTemplate.delete(PW_RESET_CODE_PREFIX + email);
        redisTemplate.opsForValue().set(PW_RESET_VERIFIED_PREFIX + email, "true", 30, TimeUnit.MINUTES);
    }

    @Transactional
    public void resetPassword(String email, String newPassword) {
        // 인증 여부 확인
        String isVerified = redisTemplate.opsForValue().get(PW_RESET_VERIFIED_PREFIX + email);
        if (isVerified == null || !"true".equals(isVerified)) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED); // 재사용. 의미상 맞음
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        user.updatePassword(passwordEncoder.encode(newPassword)); // User 엔티티에 메서드 추가 필요
        redisTemplate.delete(PW_RESET_VERIFIED_PREFIX + email);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@"))
            return email;
        int atIndex = email.indexOf("@");
        if (atIndex <= 2)
            return email; // 너무 짧으면 그대로

        String id = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (id.length() > 2) {
            return id.substring(0, 2) + "**" + domain;
        }
        return id + "**" + domain;
    }

    private static final String RT_PREFIX = "RT:";

    @Transactional
    public TokenDto login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }

        // 기존 isEmailVerified 체크는 유지하거나, 회원가입 시 true로 들어가므로 생략 가능하나 안전을 위해 유지
        if (!user.isEmailVerified()) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // 기본 프로필 이미지가 없는 경우 설정
        if (user.getProfileImage() == null || user.getProfileImage().isEmpty()) {
            user.updateProfileImage(DEFAULT_PROFILE_IMAGE);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), "ROLE_USER");
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail(), "ROLE_USER");

        // Refresh Token Redis 저장 (7일)
        redisTemplate.opsForValue().set(RT_PREFIX + user.getEmail(), refreshToken, 7, TimeUnit.DAYS);

        return TokenDto.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional
    public void logout(String email) {
        if (redisTemplate.opsForValue().get(RT_PREFIX + email) != null) {
            redisTemplate.delete(RT_PREFIX + email);
        }
    }

    @Transactional
    public TokenDto reissue(String refreshToken) {
        // 1. Refresh Token 검증
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN); // ErrorCode 확인 필요
        }

        // 2. Access Token에서 User email 가져오기 (만료되었어도 claim은 파싱 가능 혹은 Refresh Token에서
        // 가져오기)
        // Refresh Token도 JWT이므로 바로 파싱 가능
        org.springframework.security.core.Authentication authentication = jwtProvider.getAuthentication(refreshToken);
        org.ssafy.eeum.global.auth.model.CustomUserDetails userDetails = (org.ssafy.eeum.global.auth.model.CustomUserDetails) authentication
                .getPrincipal();
        String email = userDetails.getEmail();

        // 3. Redis에서 Refresh Token 저장 확인
        String storedRefreshToken = redisTemplate.opsForValue().get(RT_PREFIX + email);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 4. 새로운 토큰 생성
        String newAccessToken = jwtProvider.createAccessToken(userDetails.getId(), userDetails.getName(),
                userDetails.getRole());
        String newRefreshToken = jwtProvider.createRefreshToken(userDetails.getId(), userDetails.getName(),
                userDetails.getRole());

        // 5. Refresh Token Rotation (Redis 업데이트)
        redisTemplate.opsForValue().set(RT_PREFIX + email, newRefreshToken, 7, TimeUnit.DAYS);

        return TokenDto.builder()
                .grantType("Bearer")
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}
