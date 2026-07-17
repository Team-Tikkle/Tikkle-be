package com.tikkle.global.security;

import com.tikkle.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 내부에서 활용되는 사용자 인증 객체(Principal) 입니다.
 */
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {
    private final User user;

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    public String getPhoneNumber() {
        return user.getPhoneNumber();
    }

    @Override
    public String getUsername() {
        return user.getPhoneNumber();
    }

    // 탈퇴가 완전 삭제이므로 조회된 유저는 항상 활성 상태다.
    @Override
    public boolean isEnabled() {
        return true;
    }
}