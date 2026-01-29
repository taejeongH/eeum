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
                .isEmailVerified(true) // 이미 인증됨
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        redisTemplate.delete(AUTH_VERIFIED_PREFIX + request.getEmail());
    }

    private String generateRandomCode() {
        return String.valueOf((int)(Math.random() * 900000) + 100000);
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
}
