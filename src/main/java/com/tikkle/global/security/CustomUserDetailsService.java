package com.tikkle.global.security;

import com.tikkle.user.entity.User;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security에서 사용자 인증을 위해 DB로부터 유저 정보를 조회하는 서비스입니다.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String phoneNumber) throws UsernameNotFoundException {
        final User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(UserNotFoundException::new);
        return new CustomUserDetails(user);
    }

    /**
     * 사용자 식별자(userId)를 기반으로 DB에서 유저 정보를 조회하여 UserDetails 객체를 생성합니다.
     *
     * @param userId 조회할 사용자의 식별자
     * @return 스프링 시큐리티에서 사용할 UserDetails 객체
     * @throws UserNotFoundException 해당 ID의 사용자가 존재하지 않을 때 발생
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserByUserId(Long userId) {
        final User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return new CustomUserDetails(user);
    }
}