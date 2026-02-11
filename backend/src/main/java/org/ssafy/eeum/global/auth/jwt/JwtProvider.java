package org.ssafy.eeum.global.auth.jwt;

import io.jsonwebtoken.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.ssafy.eeum.global.auth.model.CustomUserDetails;
import org.ssafy.eeum.global.auth.model.DeviceDetails;
import org.ssafy.eeum.domain.auth.entity.User;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * JWT(JSON Web Token)의 생성, 파싱 및 유효성 검증을 담당하는 클래스입니다.
 * 비대칭 키(RS256) 방식을 사용하여 보안성을 높였으며, 사용자 및 IoT 기기용 토큰을 구분하여 처리합니다.
 * 
 * @summary JWT 제공 및 관리 서비스
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private static final String AUTH_CLAIM = "auth";
    private static final String ID_CLAIM = "id";
    private static final String GROUP_ID_CLAIM = "group_id";
    private static final String DEVICE_ROLE = "ROLE_DEVICE";

    private final JwtProperties jwtProperties;
    private final KeyUtils keyUtils;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        this.privateKey = keyUtils.loadPrivateKey();
        this.publicKey = keyUtils.loadPublicKey();
    }

    /**
     * Authentication 객체를 기반으로 액세스 토큰을 생성합니다.
     * 
     * @summary 액세스 토큰 생성 (Authentication)
     * @param authentication 인증된 사용자 정보
     * @return 생성된 JWT 액세스 토큰
     */
    public String createAccessToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return createAccessToken(userDetails.getId(), userDetails.getName(), userDetails.getRole());
    }

    /**
     * Authentication 객체를 기반으로 리프레시 토큰을 생성합니다.
     * 
     * @summary 리프레시 토큰 생성 (Authentication)
     * @param authentication 인증된 사용자 정보
     * @return 생성된 JWT 리프레시 토큰
     */
    public String createRefreshToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return createRefreshToken(userDetails.getId(), userDetails.getName(), userDetails.getRole());
    }

    /**
     * 사용자 정보를 직접 전달받아 액세스 토큰을 생성합니다.
     * 
     * @summary 액세스 토큰 생성
     * @param userId 사용자 식별자
     * @param email  사용자 이메일 (Subject)
     * @param role   사용자 권한
     * @return 생성된 JWT 액세스 토큰
     */
    public String createAccessToken(Number userId, String email, String role) {
        return createToken(userId, email, role, null, jwtProperties.getAccessTokenExpiration());
    }

    /**
     * 사용자 정보를 직접 전달받아 리프레시 토큰을 생성합니다.
     * 
     * @summary 리프레시 토큰 생성
     * @param userId 사용자 식별자
     * @param email  사용자 이메일 (Subject)
     * @param role   사용자 권한
     * @return 생성된 JWT 리프레시 토큰
     */
    public String createRefreshToken(Number userId, String email, String role) {
        return createToken(userId, email, role, null, jwtProperties.getRefreshTokenExpiration());
    }

    /**
     * IoT 기기용 장기 유효 액세스 토큰을 생성합니다 (1년).
     * 
     * @summary 기기용 액세스 토큰 생성
     * @param groupId 기기가 소속된 가족 식별자
     * @return 생성된 JWT 기기 액세스 토큰
     */
    public String createDeviceAccessToken(Integer groupId) {
        long oneYear = 1000L * 60 * 60 * 24 * 365;
        return createToken(0, "GROUP:" + groupId, DEVICE_ROLE, groupId, oneYear);
    }

    /**
     * IoT 기기용 리프레시 토큰을 생성합니다.
     * 
     * @summary 기기용 리프레시 토큰 생성
     * @param groupId 기기가 소속된 가족 식별자
     * @return 생성된 JWT 기기 리프레시 토큰
     */
    public String createDeviceRefreshToken(Integer groupId) {
        return createToken(0, "GROUP:" + groupId, DEVICE_ROLE, groupId, jwtProperties.getRefreshTokenExpiration());
    }

    /**
     * 공통 토큰 생성 로직입니다.
     * 
     * @summary 공통 토큰 생성 로직
     * @param userId     사용자 식별자 (기기일 경우 0)
     * @param email      사용자 이메일 또는 기기 그룹 식별자
     * @param role       권한 정보 (UserRole/DeviceRole)
     * @param groupId    기기 소속 그룹 ID (사용자일 경우 null)
     * @param expiration 토큰 만료 시간 (ms)
     * @return 생성된 JWT 문자열
     */
    private String createToken(Number userId, String email, String role, Integer groupId, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        JwtBuilder builder = Jwts.builder()
                .subject(email)
                .claim(ID_CLAIM, userId)
                .claim(AUTH_CLAIM, role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256); // RS256 비대칭 키 서명

        if (groupId != null) {
            builder.claim(GROUP_ID_CLAIM, groupId); // IoT 기기용 그룹 정보 추가
        }

        return builder.compact();
    }

    /**
     * 토큰에서 사용자 인증 정보를 추출합니다.
     * 
     * @summary 인증 정보 추출
     * @param token JWT 토큰
     * @return Spring Security Authentication 객체
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        Collection<? extends GrantedAuthority> authorities = extractAuthorities(claims);

        if (isDeviceToken(authorities)) {
            return createDeviceAuthentication(claims, token, authorities);
        }
        return createUserAuthentication(claims, token, authorities);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Collection<? extends GrantedAuthority> extractAuthorities(Claims claims) {
        return Arrays.stream(claims.get(AUTH_CLAIM).toString().split(","))
                .filter(auth -> !auth.trim().isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    private boolean isDeviceToken(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().anyMatch(a -> a.getAuthority().equals(DEVICE_ROLE));
    }

    private Authentication createDeviceAuthentication(Claims claims, String token,
            Collection<? extends GrantedAuthority> authorities) {
        Integer groupId = claims.get(GROUP_ID_CLAIM, Integer.class);
        DeviceDetails principal = new DeviceDetails(claims.getSubject(), groupId, authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    private Authentication createUserAuthentication(Claims claims, String token,
            Collection<? extends GrantedAuthority> authorities) {
        User user = User.builder()
                .id(claims.get(ID_CLAIM, Integer.class))
                .email(claims.getSubject())
                .name("Unknown")
                .build();
        CustomUserDetails principal = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    /**
     * 토큰의 유효성 및 만료 여부를 검증합니다.
     * 
     * @summary 토큰 검증
     * @param token JWT 토큰
     * @return 유효할 경우 true, 그렇지 않을 경우 false
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("유효하지 않은 JWT 서명입니다: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰입니다: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 토큰입니다: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT 클레임 문자열이 비어 있습니다: {}", e.getMessage());
        }
        return false;
    }
}
