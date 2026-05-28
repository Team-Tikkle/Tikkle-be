package com.tikkle.auth.service;

import com.tikkle.auth.dto.request.ReissueRequest;
import com.tikkle.auth.dto.response.TokenResponse;
import com.tikkle.auth.entity.RefreshToken;
import com.tikkle.auth.exception.ExpiredTokenException;
import com.tikkle.auth.exception.InvalidTokenException;
import com.tikkle.auth.jwt.JwtProvider;
import com.tikkle.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    // TODO: 소셜 로그인 구현 후 삭제
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

    public void logout(String email) {
        refreshTokenRepository.deleteById(email);
    }

    public TokenResponse reissue(ReissueRequest request) {
        final JwtProvider.TokenValidationResult result = jwtProvider.validateTokenWithResult(request.refreshToken());

        if (result == JwtProvider.TokenValidationResult.INVALID) {
            throw new InvalidTokenException();
        }
        if (result == JwtProvider.TokenValidationResult.EXPIRED) {
            throw new ExpiredTokenException();
        }

        final String email = jwtProvider.getEmail(request.refreshToken());
        final RefreshToken savedToken = refreshTokenRepository.findById(email)
                .orElseThrow(InvalidTokenException::new);

        if (!savedToken.getToken().equals(request.refreshToken())) {
            throw new InvalidTokenException();
        }

        final String newAccessToken = jwtProvider.createAccessToken(email);
        final String newRefreshToken = jwtProvider.createRefreshToken(email);

        refreshTokenRepository.save(new RefreshToken(
                email,
                newRefreshToken,
                jwtProvider.getRefreshTokenExpiration() / 1000
        ));

        return new TokenResponse(newAccessToken, newRefreshToken);
    }
}