package org.ssafy.eeum.global.auth.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.ssafy.eeum.domain.auth.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Spring Security의 UserDetails와 OAuth2User 인터페이스를 구현한 클래스입니다.
 * 인증된 사용자의 상세 정보 및 소셜 로그인 속성을 통합 관리합니다.
 * 
 * @summary 커스텀 사용자 상세 정보 객체
 */
public class CustomUserDetails implements UserDetails, OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;

    /**
     * 사용자 엔티티와 소셜 로그인 속성을 전달받아 객체를 생성합니다.
     * 
     * @summary 생성자 (사용자 + 속성)
     * @param user       사용자 엔티티
     * @param attributes 소셜 로그인 속성 맵
     */
    public CustomUserDetails(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    public CustomUserDetails(User user) {
        this.user = user;
        this.attributes = null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return String.valueOf(user.getId());
    }

    public String getEmail() {
        return user.getEmail();
    }

    public Integer getId() {
        return user.getId();
    }

    public User getUser() {
        return user;
    }

    public String getRole() {
        return "ROLE_USER";
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return user.getName();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}