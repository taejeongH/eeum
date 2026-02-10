package org.ssafy.eeum.global.auth.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security의 UserDetails 인터페이스를 구현한 IoT 기기 전용 상세 정보 클래스입니다.
 * 기기의 시리얼 번호와 소속 그룹 정보를 관리합니다.
 * 
 * @summary IoT 기기 상세 정보 객체
 */
public class DeviceDetails implements UserDetails {

    private final String serialNumber;
    private final Integer groupId;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 기기 시리얼 번호와 그룹 ID를 전달받아 객체를 생성합니다.
     * 
     * @summary 생성자 (기본)
     * @param serialNumber 기기 시리얼 번호
     * @param groupId      소속 가족 그룹 식별자
     */
    public DeviceDetails(String serialNumber, Integer groupId) {
        this.serialNumber = serialNumber;
        this.groupId = groupId;
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_DEVICE"));
    }

    public DeviceDetails(String serialNumber, Integer groupId, Collection<? extends GrantedAuthority> authorities) {
        this.serialNumber = serialNumber;
        this.groupId = groupId;
        this.authorities = authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return serialNumber;
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

    public String getSerialNumber() {
        return serialNumber;
    }

    public Integer getGroupId() {
        return groupId;
    }
}
