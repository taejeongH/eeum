package org.ssafy.eeum.global.auth.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class DeviceDetails implements UserDetails {

    private final String serialNumber;
    private final Integer groupId;
    private final Collection<? extends GrantedAuthority> authorities;

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
