package com.tikkle.auth.service;

import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.entity.RefreshToken;
import com.tikkle.auth.jwt.JwtProvider;
import com.tikkle.auth.repository.RefreshTokenRepository;
import com.tikkle.user.entity.enums.AuthProvider;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.UserStatus;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Profile("local")
@RequiredArgsConstructor
public class TestTokenService {
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public TokenResponse generateTestToken(String email) {
        userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
        return issueToken(email, false);
    }

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