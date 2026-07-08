package com.tikkle.auth.service;

import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.entity.RefreshToken;
import com.tikkle.auth.repository.RefreshTokenRepository;
import com.tikkle.global.security.jwt.JwtProvider;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.AuthProvider;
import com.tikkle.user.entity.enums.UserStatus;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 개발 환경(local)에서 프론트엔드 개발 시 편리하게 인증 토큰을 발급받기 위한 테스트 서비스입니다.
 */
@Service
@Profile("local")
@RequiredArgsConstructor
public class TestTokenService {
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    /**
     * 기존에 가입된 회원의 이메일을 이용해 강제로 JWT 토큰을 발급합니다.
     *
     * @param email 발급 대상 사용자 이메일
     * @return 엑세스 및 리프레시 토큰 응답
     * @throws UserNotFoundException 회원이 존재하지 않을 경우
     */
    public TokenResponse generateTestToken(String email) {
        userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
        return issueToken(email, false);
    }

    /**
     * 이메일과 이름을 입력받아 신규 회원을 강제로 가입시킨 후 JWT 토큰을 발급합니다.
     * 이미 존재하는 이메일일 경우 기존 유저로 로그인 처리됩니다.
     *
     * @param email 회원 이메일
     * @param name 회원 이름
     * @return 엑세스 및 리프레시 토큰 응답
     */
    public TokenResponse generateTestSignupAndToken(String email, String name) {
        final Optional<User> existingUser = userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE);
        final boolean isNewUser = existingUser.isEmpty();
        final User user = existingUser
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .name(name)
                        .provider(AuthProvider.GOOGLE)
                        .status(UserStatus.ACTIVE)
                        .build()));
        return issueToken(user.getEmail(), isNewUser);
    }

    private TokenResponse issueToken(String email, boolean isNewUser) {
        final String accessToken = jwtProvider.createAccessToken(email);
        final String refreshToken = jwtProvider.createRefreshToken(email);
        refreshTokenRepository.save(new RefreshToken(
                email,
                refreshToken,
                jwtProvider.getRefreshTokenExpiration() / 1000
        ));
        return new TokenResponse(accessToken, refreshToken, isNewUser);
    }
}