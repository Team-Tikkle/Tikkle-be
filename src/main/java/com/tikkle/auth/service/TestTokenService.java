package com.tikkle.auth.service;

import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.entity.RefreshToken;
import com.tikkle.auth.jwt.JwtProvider;
import com.tikkle.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
@RequiredArgsConstructor
public class TestTokenService {
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public TokenResponse generateTestToken(String email) {
        final String accessToken = jwtProvider.createAccessToken(email);
        final String refreshToken = jwtProvider.createRefreshToken(email);
        refreshTokenRepository.save(new RefreshToken(
                email,
                refreshToken,
                jwtProvider.getRefreshTokenExpiration() / 1000
        ));
        return new TokenResponse(accessToken, refreshToken);
    }
}
