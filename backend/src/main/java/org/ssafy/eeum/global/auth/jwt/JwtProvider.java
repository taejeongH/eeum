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

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties jwtProperties;
    private final KeyUtils keyUtils;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        this.privateKey = keyUtils.loadPrivateKey();
        this.publicKey = keyUtils.loadPublicKey();
    }

    public String createAccessToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return createAccessToken(userDetails.getId(), userDetails.getName(), userDetails.getRole());
    }

    public String createRefreshToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return createRefreshToken(userDetails.getId(), userDetails.getName(), userDetails.getRole());
    }

    public String createAccessToken(Number userId, String email, String role) {
        return createToken(userId, email, role, null, jwtProperties.getAccessTokenExpiration());
    }

    public String createRefreshToken(Number userId, String email, String role) {
        return createToken(userId, email, role, null, jwtProperties.getRefreshTokenExpiration());
    }

    public String createDeviceAccessToken(Integer groupId) {
        
        long oneYear = 1000L * 60 * 60 * 24 * 365;
        return createToken(0, "GROUP:" + groupId, "ROLE_DEVICE", groupId, oneYear);
    }

    public String createDeviceRefreshToken(Integer groupId) {
        return createToken(0, "GROUP:" + groupId, "ROLE_DEVICE", groupId, jwtProperties.getRefreshTokenExpiration());
    }

    private String createToken(Number userId, String email, String role, Integer groupId, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        JwtBuilder builder = Jwts.builder()
                .subject(email)
                .claim("id", userId)
                .claim("auth", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256);

        if (groupId != null) {
            builder.claim("group_id", groupId);
        }

        return builder.compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Collection<? extends GrantedAuthority> authorities = Arrays.stream(claims.get("auth").toString().split(","))
                .filter(auth -> !auth.trim().isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        boolean isDevice = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_DEVICE"));

        if (isDevice) {
            Integer groupId = claims.get("group_id", Integer.class);
            DeviceDetails principal = new DeviceDetails(claims.getSubject(), groupId, authorities);
            return new UsernamePasswordAuthenticationToken(principal, token, authorities);
        } else {
            User user = User.builder()
                    .id(claims.get("id", Integer.class))
                    .email(claims.getSubject())
                    .name("Unknown")
                    .build();
            CustomUserDetails principal = new CustomUserDetails(user);
            return new UsernamePasswordAuthenticationToken(principal, token, authorities);
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token compact of handler are invalid: {}", e.getMessage());
        }
        return false;
    }
}
