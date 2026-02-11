package org.ssafy.eeum.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.eeum.domain.auth.dto.request.LoginRequest;
import org.ssafy.eeum.domain.auth.dto.request.SignupRequest;
import org.ssafy.eeum.domain.auth.entity.User;
import org.ssafy.eeum.domain.auth.repository.UserRepository;
import org.ssafy.eeum.global.auth.jwt.JwtProvider;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.ssafy.eeum.global.auth.model.TokenDto;
import org.ssafy.eeum.global.email.service.EmailService;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 사용자 인증 및 계정 관리를 담당하는 서비스 클래스입니다.
 * 회원가입, 로그인, 로그아웃, 비밀번호 재설정 및 토큰 재발급 기능을 제공합니다.
 * 
 * @summary 인증 및 계정 관리 서비스
 */
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

    /**
     * 회원가입을 위한 인증 코드를 이메일로 전송합니다.
     * 
     * @summary 이메일 인증 코드 전송
     * @param email 수신 이메일 주소
     */
    @Transactional
    public void sendCode(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        String code = generateRandomCode();
        redisTemplate.opsForValue().set(AUTH_CODE_PREFIX + email, code, 3, TimeUnit.MINUTES);
        emailService.sendVerificationCode(email, code);
    }

    /**
     * 전송된 이메일 인증 코드를 검증합니다.
     * 
     * @summary 이메일 인증 코드 검증
     * @param email 이메일 주소
     * @param code  인증 코드
     */
    @Transactional
    public void verifyCode(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get(AUTH_CODE_PREFIX + email);
        if (storedCode == null || !storedCode.equals(code)) {
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        redisTemplate.delete(AUTH_CODE_PREFIX + email);
        redisTemplate.opsForValue().set(AUTH_VERIFIED_PREFIX + email, "true", 30, TimeUnit.MINUTES);
    }

    /**
     * 새로운 사용자를 등록합니다. 이메일 인증이 완료된 상태여야 합니다.
     * 
     * @summary 회원가입 처리
     * @param request 회원가입 요청 정보
     */
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
                .isEmailVerified(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        redisTemplate.delete(AUTH_VERIFIED_PREFIX + request.getEmail());
    }

    /**
     * 6자리의 무작위 인증 코드를 생성합니다.
     * 
     * @summary 인증 코드 생성
     * @return 생성된 코드 문자열
     */
    private String generateRandomCode() {
        return String.valueOf((int) (Math.random() * 900000) + 100000);
    }

    /**
     * 이름과 전화번호를 기반으로 마스킹된 이메일 주소를 찾습니다.
     * 
     * @summary 이메일 찾기
     * @param name  사용자 이름
     * @param phone 전화번호
     * @return 마스킹된 이메일 주소
     */
    @Transactional(readOnly = true)
    public String findEmail(String name, String phone) {
        List<User> users = userRepository.findByNameAndPhone(name, phone);
        if (users.isEmpty()) {
            throw new CustomException(ErrorCode.ENTITY_NOT_FOUND);
        }

        String email = users.get(0).getEmail();
        return maskEmail(email);
    }

    /**
     * 비밀번호 재설정을 위한 인증 코드를 이메일로 전송합니다.
     * 
     * @summary 비밀번호 재설정 코드 전송
     * @param email 수신 이메일 주소
     */
    @Transactional
    public void sendPasswordResetCode(String email) {
        if (userRepository.findByEmail(email).isEmpty()) {
            throw new CustomException(ErrorCode.ENTITY_NOT_FOUND);
        }

        String code = generateRandomCode();
        redisTemplate.opsForValue().set(PW_RESET_CODE_PREFIX + email, code, 3, TimeUnit.MINUTES);
        emailService.sendVerificationCode(email, code);
    }

    /**
     * 비밀번호 재설정 인증 코드를 검증합니다.
     * 
     * @summary 비밀번호 재설정 코드 검증
     * @param email 이메일 주소
     * @param code  인증 코드
     */
    @Transactional
    public void verifyPasswordResetCode(String email, String code) {
        String storedCode = redisTemplate.opsForValue().get(PW_RESET_CODE_PREFIX + email);
        if (storedCode == null || !storedCode.equals(code)) {
            throw new CustomException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        redisTemplate.delete(PW_RESET_CODE_PREFIX + email);
        redisTemplate.opsForValue().set(PW_RESET_VERIFIED_PREFIX + email, "true", 30, TimeUnit.MINUTES);
    }

    /**
     * 사용자의 비밀번호를 새로운 비밀번호로 변경합니다.
     * 
     * @summary 비밀번호 재설정 실행
     * @param email       이메일 주소
     * @param newPassword 새 비밀번호
     */
    @Transactional
    public void resetPassword(String email, String newPassword) {
        String isVerified = redisTemplate.opsForValue().get(PW_RESET_VERIFIED_PREFIX + email);
        if (isVerified == null || !"true".equals(isVerified)) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));

        user.updatePassword(passwordEncoder.encode(newPassword));
        redisTemplate.delete(PW_RESET_VERIFIED_PREFIX + email);
    }

    /**
     * 이메일 주소의 일부를 마스킹 처리합니다.
     * 
     * @summary 이메일 마스킹 처리
     * @param email 이메일 주소
     * @return 마스킹된 이메일 주소
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@"))
            return email;
        int atIndex = email.indexOf("@");
        if (atIndex <= 2)
            return email;

        String id = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (id.length() > 2) {
            return id.substring(0, 2) + "**" + domain;
        }
        return id + "**" + domain;
    }

    private static final String RT_PREFIX = "RT:";

    /**
     * 사용자 로그인을 처리하고 JWT 토큰 세트를 발급합니다.
     * 
     * @summary 로그인 처리
     * @param request 로그인 정보
     * @return 토큰 정보 DTO
     */
    @Transactional
    public TokenDto login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }

        if (!user.isEmailVerified()) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (user.getProfileImage() == null || user.getProfileImage().isEmpty()) {
            user.updateProfileImage(DEFAULT_PROFILE_IMAGE);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), "ROLE_USER");
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail(), "ROLE_USER");

        redisTemplate.opsForValue().set(RT_PREFIX + user.getEmail(), refreshToken, 7, TimeUnit.DAYS);

        return TokenDto.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    /**
     * 로그아웃을 처리하고 저장된 리프레시 토큰을 삭제합니다.
     * 
     * @summary 로그아웃 처리
     * @param email 로그아웃할 사용자 이메일
     */
    @Transactional
    public void logout(String email) {
        if (redisTemplate.opsForValue().get(RT_PREFIX + email) != null) {
            redisTemplate.delete(RT_PREFIX + email);
        }
    }

    /**
     * 유효한 리프레시 토큰을 통해 새로운 액세스 토큰과 리프레시 토큰을 재발급합니다.
     * 
     * @summary 토큰 재발급(Reissue)
     * @param refreshToken 리프레시 토큰
     * @return 새로운 토큰 정보 DTO
     */
    @Transactional
    public TokenDto reissue(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Authentication authentication = jwtProvider.getAuthentication(refreshToken);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = userDetails.getEmail();

        String storedRefreshToken = redisTemplate.opsForValue().get(RT_PREFIX + email);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String newAccessToken = jwtProvider.createAccessToken(userDetails.getId(), userDetails.getName(),
                userDetails.getRole());
        String newRefreshToken = jwtProvider.createRefreshToken(userDetails.getId(), userDetails.getName(),
                userDetails.getRole());

        redisTemplate.opsForValue().set(RT_PREFIX + email, newRefreshToken, 7, TimeUnit.DAYS);

        return TokenDto.builder()
                .grantType("Bearer")
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}
