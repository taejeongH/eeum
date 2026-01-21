package org.ssafy.eeum.global.auth.model;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Getter
public class CustomUserDetails implements UserDetails, OAuth2User {

    private final Long id;
    private final String email;
    private final String socialId;
    private final String provider;
    private final String role;
    private final Map<String, Object> attributes;

    @Builder
    public CustomUserDetails(Long id, String email, String socialId, String provider, String role, Map<String, Object> attributes) {
        this.id = id;
        this.email = email;
        this.socialId = socialId;
        this.provider = provider;
        this.role = role;
        this.attributes = attributes;
    }

    /**
     * 사용 시나리오
     * 1. 로컬 로그인 : socialId=null, provider="local", attributes=null
     * 2. 소셜 로그인 : socialId="12345", proivder="kakao", attributes={...}
     */

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return String.valueOf(id);
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}